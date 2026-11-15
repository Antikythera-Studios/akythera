package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;

/// A notify state that holds back the owner's jumps from the ground in its time window, on any animation the animator
/// plays (a montage or a state machine's).
///
/// Read through [Animator#getActiveNotifyState] every tick, so the callbacks do nothing.
public final class BlockJumpNotifyState extends AnimationNotifyState<LivingEntityPatch<?>> {
    public static final MapCodec<BlockJumpNotifyState> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("min_fall_height", 0.0F).forGetter(BlockJumpNotifyState::minFallHeight)
            ).apply(instance, BlockJumpNotifyState::new)
    );

    /// The window holds jumps back only after a fall at least this high, in blocks, see [EntityPatch#getFallHeight].
    private final float minFallHeight;

    public BlockJumpNotifyState(final float minFallHeight) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);
        this.minFallHeight = minFallHeight;
    }

    public float minFallHeight() {
        return minFallHeight;
    }

    @Override
    public void onBegin(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
    }

    @Override
    public void onTick(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void onEnd(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
