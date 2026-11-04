package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;

/// Strikes the first entity on the owner's view ray within [#range] blocks, by [LivingEntityPatch#hurtTarget] with
/// vanilla attack effects. Blocks stop the ray.
public final class LivingHurtNotify extends AnimationNotify<LivingEntityPatch<?>> {
    public static final MapCodec<LivingHurtNotify> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            Codec.doubleRange(0.0D, Double.MAX_VALUE).optionalFieldOf("range", 3.0D).forGetter(LivingHurtNotify::range)
        ).apply(inst, LivingHurtNotify::new)
    );

    /// Length of the view ray from the owner's eyes, in blocks.
    private final double range;

    public LivingHurtNotify(final double range) {
        super(LogicalSide.SERVER, NotifyTriggerThread.WORLD);

        this.range = range;
    }

    @Override
    public void onNotified(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        Entity attacker = owner.getOriginal();

        if (ProjectileUtil.getHitResultOnViewVector(attacker, entity -> entity.isAttackable() && entity.isAlive(), range) instanceof EntityHitResult hit) {
            owner.hurtTarget(hit.getEntity(), true);
        }
    }

    public double range() {
        return range;
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}
