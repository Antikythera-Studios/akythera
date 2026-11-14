package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/// A notify state that takes gravity off its owner in its time window: [Entity#getGravity] answers zero, and the
/// owner's vertical speed is dropped when the window begins.
///
/// Read through [AnimationMontagePlayer#getActiveNotifyState] every tick, on both sides.
public final class NoGravityNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<NoGravityNotifyState> CODEC = MapCodec.unit(NoGravityNotifyState::new);

    public NoGravityNotifyState() {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);
    }

    @Override
    public void onBegin(final EntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        Vec3 deltaMovement = owner.getOriginal().getDeltaMovement();

        owner.getOriginal().setDeltaMovement(deltaMovement.x, 0.0D, deltaMovement.z);
    }

    @Override
    public void onTick(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void onEnd(final EntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
