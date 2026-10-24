package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.LivingEntity;

/// A notify state that keeps a dead owner from being removed by its death count in its time window: the
/// [LivingEntity#deathTime] stays under the 20 ticks at which vanilla poofs and removes the entity.
///
/// Read through [AnimationMontagePlayer#getActiveNotifyState] every tick, on both sides, so the callbacks do nothing.
public final class ExtendDeathTimeNotifyState extends AnimationNotifyState<LivingEntityPatch<?>> {
    public static final MapCodec<ExtendDeathTimeNotifyState> CODEC = MapCodec.unit(ExtendDeathTimeNotifyState::new);

    public ExtendDeathTimeNotifyState() {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);
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
