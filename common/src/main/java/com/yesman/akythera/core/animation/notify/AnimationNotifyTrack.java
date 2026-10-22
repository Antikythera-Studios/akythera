package com.yesman.akythera.core.animation.notify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/// A timeline of [AnimationNotify] (point-in-time triggers) and
/// [AnimationNotifyState] (windowed triggers) attached to a [PlayableAnimation].
/// Conducts Side-validation predicate so each owner can reject notifies whose
/// [LogicalSide] doesn't match its replication contract:
///   - AnimationMontage: accepts everything (no filter).
///   - AnimationSequence: client-only — server notifies would never fire on
///     the dedicated server thread.
public final class AnimationNotifyTrack {
    public static final AnimationNotifyTrack EMPTY = new AnimationNotifyTrack(List.of(), List.of());

    /// Codec for the full track. Stored as two flat lists so JSON ordering
    /// is stable and human-editable.
    public static final Codec<AnimationNotifyTrack> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                NotifyInTimeStamp.CODEC.listOf().optionalFieldOf("notifies", List.of())
                    .forGetter(AnimationNotifyTrack::serializeNotifies),
                NotifyStateInTimeWindow.CODEC.listOf().optionalFieldOf("notify_states", List.of())
                    .forGetter(AnimationNotifyTrack::serializeNotifyStates)
            )
            .apply(instance, AnimationNotifyTrack::new)
    );

    /// Sorted by time stamp. Notifies at the same time are all kept, in no set order.
    private final List<NotifyInTimeStamp> worldNotifies;
    private final List<NotifyInTimeStamp> renderNotifies;

    /// Sorted by the window's start. Notify states starting at the same time are all kept, in no set order.
    private final List<NotifyStateInTimeWindow> worldNotifyStates;
    private final List<NotifyStateInTimeWindow> renderNotifyStates;

    public AnimationNotifyTrack(final List<NotifyInTimeStamp> notifies, final List<NotifyStateInTimeWindow> notifyStates) {
        this.worldNotifies = notifies.stream().filter(setup -> setup.animNotify().triggeringThread() == NotifyTriggerThread.WORLD).sorted().toList();
        this.renderNotifies = notifies.stream().filter(setup -> setup.animNotify().triggeringThread() == NotifyTriggerThread.RENDER).sorted().toList();
        this.worldNotifyStates = notifyStates.stream().filter(setup -> setup.animNotifyState().triggeringThread() == NotifyTriggerThread.WORLD).sorted().toList();
        this.renderNotifyStates = notifyStates.stream().filter(setup -> setup.animNotifyState().triggeringThread() == NotifyTriggerThread.RENDER).sorted().toList();
    }

    /// Runs the [NotifyTriggerThread#WORLD] notifies the playback passed in a tick, and on a server the
    /// [NotifyTriggerThread#RENDER] ones too.
    public void tick(
        final PlayableAnimation playingAnimation,
        final ComponentAttachable owner,
        final float prevElapsedTime,
        final float currentElapsedTime
    ) {
        activate(worldNotifies, worldNotifyStates, playingAnimation, owner, prevElapsedTime, currentElapsedTime);

        if (owner.logicalSide() == LogicalSide.SERVER) {
            activate(renderNotifies, renderNotifyStates, playingAnimation, owner, prevElapsedTime, currentElapsedTime);
        }
    }

    /// Runs the [NotifyTriggerThread#RENDER] notifies the playback passed in a frame, on a client.
    public void frameUpdate(
        final PlayableAnimation playingAnimation,
        final ComponentAttachable owner,
        final float prevElapsedTime,
        final float currentElapsedTime
    ) {
        if (owner.logicalSide() == LogicalSide.CLIENT) {
            activate(renderNotifies, renderNotifyStates, playingAnimation, owner, prevElapsedTime, currentElapsedTime);
        }
    }

    /// Visits only the notifies in `(prevElapsedTime, currentElapsedTime]`, found by a binary search, and the notify
    /// states that start by `currentElapsedTime`.
    private static void activate(
        final List<NotifyInTimeStamp> notifies,
        final List<NotifyStateInTimeWindow> notifyStates,
        final PlayableAnimation playingAnimation,
        final ComponentAttachable owner,
        final float prevElapsedTime,
        final float currentElapsedTime
    ) {
        // The first notify past `prevElapsedTime`
        int low = 0;
        int high = notifies.size();

        while (low < high) {
            int mid = (low + high) >>> 1;

            if (notifies.get(mid).timeStamp() <= prevElapsedTime) low = mid + 1;
            else high = mid;
        }

        for (int i = low; i < notifies.size() && notifies.get(i).timeStamp() <= currentElapsedTime; i++) {
            notifies.get(i).animNotify().tryActivate(playingAnimation, owner, prevElapsedTime, currentElapsedTime);
        }

        for (NotifyStateInTimeWindow setup : notifyStates) {
            if (setup.timeWindow().from() > currentElapsedTime) break;

            if (setup.timeWindow().windowOverlaps(prevElapsedTime, currentElapsedTime)) {
                setup.animNotifyState().tryActivate(playingAnimation, owner, setup.timeWindow(), prevElapsedTime, currentElapsedTime);
            }
        }
    }

    /// Ends every notify state the playback sits inside, for an animation stopped there.
    ///
    /// @param lastTickTime  the time the last [#tick] ran to
    /// @param lastFrameTime the time the last [#frameUpdate] ran to
    public void interrupt(final PlayableAnimation playingAnimation, final ComponentAttachable owner, final float lastTickTime, final float lastFrameTime) {
        float renderTime = owner.logicalSide() == LogicalSide.SERVER ? lastTickTime : lastFrameTime;

        worldNotifyStates.forEach(setup -> {
            if (setup.timeWindow().isIn(lastTickTime)) setup.animNotifyState().tryInterrupt(playingAnimation, owner);
        });
        renderNotifyStates.forEach(setup -> {
            if (setup.timeWindow().isIn(renderTime)) setup.animNotifyState().tryInterrupt(playingAnimation, owner);
        });
    }

    /// For codec encoding
    public List<NotifyInTimeStamp> serializeNotifies() {
        return Stream.concat(worldNotifies.stream(), renderNotifies.stream()).sorted().toList();
    }

    /// For codec encoding
    public List<NotifyStateInTimeWindow> serializeNotifyStates() {
        return Stream.concat(worldNotifyStates.stream(), renderNotifyStates.stream()).sorted().toList();
    }

    /// Returns the first notify state of `type` whose time window holds `time`, or null.
    public <T extends AnimationNotifyState<?>> @Nullable T getNotifyStateAt(final Class<T> type, final float time) {
        return Stream.concat(worldNotifyStates.stream(), renderNotifyStates.stream())
            .filter(setup -> setup.timeWindow().isIn(time) && type.isInstance(setup.animNotifyState()))
            .map(setup -> type.cast(setup.animNotifyState()))
            .findFirst()
            .orElse(null);
    }

    public boolean isEmpty() {
        return worldNotifies.isEmpty() && renderNotifies.isEmpty() && worldNotifyStates.isEmpty() && renderNotifyStates.isEmpty();
    }

    public record NotifyInTimeStamp(AnimationNotify<?> animNotify, float timeStamp)
        implements Comparable<NotifyInTimeStamp>
    {
        public static final Codec<NotifyInTimeStamp> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AnimationNotify.CODEC.fieldOf("notify").forGetter(NotifyInTimeStamp::animNotify),
                    Codec.FLOAT.fieldOf("time").forGetter(NotifyInTimeStamp::timeStamp)
                )
                .apply(instance, NotifyInTimeStamp::new)
        );

        @Override
        public int compareTo(@NonNull NotifyInTimeStamp o) {
            return Float.compare(timeStamp, o.timeStamp);
        }
    }

    public record NotifyStateInTimeWindow(AnimationNotifyState<?> animNotifyState, TimeWindow timeWindow)
        implements Comparable<NotifyStateInTimeWindow>
    {
        public static final Codec<NotifyStateInTimeWindow> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AnimationNotifyState.CODEC.fieldOf("notify_state").forGetter(NotifyStateInTimeWindow::animNotifyState),
                    TimeWindow.CODEC.fieldOf("time_window").forGetter(NotifyStateInTimeWindow::timeWindow)
                )
                .apply(instance, NotifyStateInTimeWindow::new)
        );

        @Override
        public int compareTo(@NonNull NotifyStateInTimeWindow o) {
            return Float.compare(timeWindow().from(), o.timeWindow().from());
        }
    }
}
