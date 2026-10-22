package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;

/// A notify state that stops the montage once the owner tries to move in its time window.
///
/// Read through [AnimationMontagePlayer#getActiveNotifyState] every tick, so the callbacks do nothing.
public final class CancelOnMoveNotifyState extends AnimationNotifyState<LivingEntityPatch<?>> {
    public static final MapCodec<CancelOnMoveNotifyState> CODEC = MapCodec.unit(CancelOnMoveNotifyState::new);

    public CancelOnMoveNotifyState() {
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
