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

    public AnimationNotifyState(LogicalSide logicalSide) {
        this.logicalSide = logicalSide;
    }

    /// Checks elapsed times and activate notify in condition
    @ApiStatus.Internal @SuppressWarnings("unchecked")
    public final void tryActivate(PlayableAnimation animation, final ComponentAttachable owner, TimeWindow timeWindow, float prevElapsedTime, float currentElapsedTime) {
        if (!logicalSide.accepts(owner.logicalSide())) {
            return;
        }

        try {
            T casted = (T)owner;

            if (prevElapsedTime < timeWindow.from() && timeWindow.from() <= currentElapsedTime) {
                this.onBegin(casted, animation);
            }

            if (timeWindow.from() < currentElapsedTime && prevElapsedTime <= timeWindow.to()) {
                this.onTick(casted, animation, prevElapsedTime, currentElapsedTime);
            }

            if (prevElapsedTime < timeWindow.to() && timeWindow.to() <= currentElapsedTime) {
                this.onEnd(casted, animation);
            }
        } catch(ClassCastException _) {
        }
    }

    /// Called when notify triggered at first. Note that [#onTick] is triggered in the same tick
    public abstract void onBegin(T owner, PlayableAnimation animation);

    /// Called in each tick when notify is in time window
    public abstract void onTick(T owner, PlayableAnimation animation, float prevElapsedTime, float currentElapsedTime);

    /// Called when the playback time gets through the last time window. Note that [#onTick] is triggered in the same tick
    public abstract void onEnd(T owner, PlayableAnimation animation);

    public final LogicalSide logicalSide() {
        return logicalSide;
    }

    /// Returns map codec for serializing this notify
    public abstract MapCodec<? extends AnimationNotifyState<?>> mapCodec();
}
