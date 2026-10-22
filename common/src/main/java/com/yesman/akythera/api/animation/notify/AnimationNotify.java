package com.yesman.akythera.api.animation.notify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/// A callback instance to execute a task once on specific time stamp
///
/// Inspired by Unreal Engine 5 [Animation Notify](https://dev.epicgames.com/documentation/unreal-engine/animation-notifies-in-unreal-engine)
/// system
public abstract class AnimationNotify<T extends ComponentAttachable> {
    public static final Codec<AnimationNotify<?>> CODEC = AkytheraRegistries.Frozen.ANIM_NOTIFY_TYPES
        .byNameCodec()
        .dispatch(AnimationNotify::mapCodec, Function.identity());

    protected final LogicalSide logicalSide;
    protected final NotifyTriggerThread triggeringThread;

    public AnimationNotify(final LogicalSide logicalSide, final NotifyTriggerThread triggeringThread) {
        this.logicalSide = logicalSide;
        this.triggeringThread = triggeringThread;
    }

    /// Tries activating notify after the logical side validation, receives current and previous elapsed times.
    /// Notify won't be triggered if `owner` is not assignable to <T>.
    @ApiStatus.Internal @SuppressWarnings("unchecked")
    public final void tryActivate(final PlayableAnimation animation, final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime) {
        if (!logicalSide.accepts(owner.logicalSide())) {
            return;
        }

        try {
            T casted = (T)owner;

            this.onNotified(casted, animation, prevElapsedTime, currentElapsedTime);
        } catch(ClassCastException _) {
        }
    }

    public LogicalSide logicalSide() {
        return logicalSide;
    }

    public NotifyTriggerThread triggeringThread() {
        return triggeringThread;
    }

    /// A task that is executed once an animation playback time reaches the timestamp
    public abstract void onNotified(final T owner, PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime);

    /// Returns map codec for serializing this notify
    public abstract MapCodec<? extends AnimationNotify<?>> mapCodec();
}
