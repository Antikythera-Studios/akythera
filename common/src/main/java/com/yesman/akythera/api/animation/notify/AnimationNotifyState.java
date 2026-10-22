package com.yesman.akythera.api.animation.notify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/// A callback instance to execute a task continuously in a timed window
///
/// Ensures [#onEnd] to be called no matter the notify ends
///
/// Inspired by Unreal Engine 5 [Notify State](https://dev.epicgames.com/documentation/en-us/unreal-engine/animation-notifies-in-unreal-engine#notifystate)
public abstract class AnimationNotifyState<T extends ComponentAttachable> {
    public static final Codec<AnimationNotifyState<?>> CODEC = AkytheraRegistries.Frozen.ANIM_NOTIFY_STATE_TYPES
        .byNameCodec()
        .dispatch(AnimationNotifyState::mapCodec, Function.identity());

    protected final LogicalSide logicalSide;
    protected final NotifyTriggerThread triggeringThread;

    public AnimationNotifyState(final LogicalSide logicalSide, final NotifyTriggerThread triggeringThread) {
        this.logicalSide = logicalSide;
        this.triggeringThread = triggeringThread;
    }

    /// Checks elapsed times and activate notify in condition
    @ApiStatus.Internal @SuppressWarnings("unchecked")
    public final void tryActivate(final PlayableAnimation animation, final ComponentAttachable owner, final TimeWindow timeWindow, final float prevElapsedTime, final float currentElapsedTime) {
        if (!logicalSide.accepts(owner.logicalSide())) {
            return;
        }

        try {
            T casted = (T)owner;

            if (prevElapsedTime < timeWindow.from() && timeWindow.from() <= currentElapsedTime) {
                this.onBegin(casted, animation, timeWindow);
            }

            if (timeWindow.from() < currentElapsedTime && prevElapsedTime <= timeWindow.to()) {
                this.onTick(casted, animation, prevElapsedTime, currentElapsedTime);
            }

            if (prevElapsedTime < timeWindow.to() && timeWindow.to() <= currentElapsedTime) {
                this.onEnd(casted, animation, false);
            }
        } catch(ClassCastException _) {
        }
    }

    /// Ends the notify early, for an animation stopped inside the notify's window
    @ApiStatus.Internal @SuppressWarnings("unchecked")
    public final void tryInterrupt(final PlayableAnimation animation, final ComponentAttachable owner) {
        if (!logicalSide.accepts(owner.logicalSide())) {
            return;
        }

        try {
            this.onEnd((T)owner, animation, true);
        } catch(ClassCastException _) {
        }
    }

    /// Called when notify triggered at first. Note that [#onTick] is triggered in the same tick
    ///
    /// @param timeWindow the window the notify runs in, in the animation's elapsed time
    public abstract void onBegin(final T owner, final PlayableAnimation animation, final TimeWindow timeWindow);

    /// Called in each tick when notify is in time window, or in each frame for a client's [NotifyTriggerThread#RENDER]
    /// notify
    public abstract void onTick(final T owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime);

    /// Called when the playback time gets through the last time window, or when the animation stops inside the window.
    /// Note that [#onTick] is triggered in the same tick
    ///
    /// @param interrupted whether the animation stopped before the window's end
    public abstract void onEnd(final T owner, final PlayableAnimation animation, final boolean interrupted);

    public final LogicalSide logicalSide() {
        return logicalSide;
    }

    public final NotifyTriggerThread triggeringThread() {
        return triggeringThread;
    }

    /// Returns map codec for serializing this notify
    public abstract MapCodec<? extends AnimationNotifyState<?>> mapCodec();
}
