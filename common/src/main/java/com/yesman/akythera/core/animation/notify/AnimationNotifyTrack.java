package com.yesman.akythera.core.animation.notify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    private final Map<Float, AnimationNotify<?>> notifies = new TreeMap<>(Float::compareTo);
    private final Map<TimeWindow, AnimationNotifyState<?>> notifyStates = new TreeMap<>(TimeWindow::compareTo);

    public AnimationNotifyTrack(final List<NotifyInTimeStamp> notifies, final List<NotifyStateInTimeWindow> notifyStates) {
        for (NotifyInTimeStamp setup : notifies) {
            this.notifies.put(setup.timeStamp(), setup.animNotify());
        }

        for (NotifyStateInTimeWindow setup : notifyStates) {
            this.notifyStates.put(setup.timeWindow(), setup.animNotifyState());
        }
    }

    /// Verbatim relocation of `AnimationMontage#tick`, but parameterized by
    /// the owning [PlayableAnimation] so notify callbacks know who fired them.
    public void tick(
        final PlayableAnimation playingAnimation,
        final ComponentAttachable owner,
        final float prevElapsedTime,
        final float currentElapsedTime
    ) {
        for (Map.Entry<Float, AnimationNotify<?>> entry : notifies.entrySet()) {
            if (prevElapsedTime < entry.getKey() && entry.getKey() <= currentElapsedTime) {
                entry.getValue().tryActivate(playingAnimation, owner, prevElapsedTime, currentElapsedTime);
            }
        }

        for (Map.Entry<TimeWindow, AnimationNotifyState<?>> entry : notifyStates.entrySet()) {
            if (entry.getKey().windowOverlaps(prevElapsedTime, currentElapsedTime)) {
                entry.getValue().tryActivate(playingAnimation, owner, entry.getKey(), prevElapsedTime, currentElapsedTime);
            }
        }
    }

    /// Ends every notify state `elapsedTime` sits inside, for an animation stopped there.
    public void interrupt(final PlayableAnimation playingAnimation, final ComponentAttachable owner, final float elapsedTime) {
        for (Map.Entry<TimeWindow, AnimationNotifyState<?>> entry : notifyStates.entrySet()) {
            if (entry.getKey().from() <= elapsedTime && elapsedTime < entry.getKey().to()) {
                entry.getValue().tryInterrupt(playingAnimation, owner);
            }
        }
    }

    /// For codec encoding
    public List<NotifyInTimeStamp> serializeNotifies() {
        return notifies.entrySet().stream()
            .map(entry -> new NotifyInTimeStamp(entry.getValue(), entry.getKey()))
            .collect(Collectors.toList());
    }

    /// For codec encoding
    public List<NotifyStateInTimeWindow> serializeNotifyStates() {
        return notifyStates.entrySet().stream()
            .map(entry -> new NotifyStateInTimeWindow(entry.getValue(), entry.getKey()))
            .collect(Collectors.toList());
    }

    /// Returns the first notify state of `type` whose time window holds `time`, or null.
    public <T extends AnimationNotifyState<?>> @Nullable T getNotifyStateAt(final Class<T> type, final float time) {
        for (Map.Entry<TimeWindow, AnimationNotifyState<?>> entry : notifyStates.entrySet()) {
            if (entry.getKey().isIn(time) && type.isInstance(entry.getValue())) return type.cast(entry.getValue());
        }

        return null;
    }

    public boolean isEmpty() {
        return notifies.isEmpty() && notifyStates.isEmpty();
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
