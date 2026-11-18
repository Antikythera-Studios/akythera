package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.BlockSlam;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/// Plays a [BlockSlam] at the owner's feet, moved by [#offset].
public final class BlockSlamNotify extends AnimationNotify<EntityPatch<?>> {
    public static final MapCodec<BlockSlamNotify> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            Codec.doubleRange(BlockSlam.MIN_RADIUS, Double.MAX_VALUE).optionalFieldOf("radius", 3.0D).forGetter(BlockSlamNotify::radius),
            Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(BlockSlamNotify::offset)
        ).apply(inst, BlockSlamNotify::new)
    );

    private final double radius; // blocks

    /// Offset from the owner's feet, in the owner's facing space: +x left, +z forward.
    private final Vec3 offset;

    public BlockSlamNotify(final double radius, final Vec3 offset) {
        super(LogicalSide.CLIENT, NotifyTriggerThread.WORLD);

        this.radius = radius;
        this.offset = offset;
    }

    @Override
    public void onNotified(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        Entity entity = owner.getOriginal();
        float yaw = entity instanceof LivingEntity livingEntity ? livingEntity.yBodyRot : entity.getYRot();

        BlockSlam.slam(entity.level(), entity.position().add(offset.yRot(-yaw * Mth.DEG_TO_RAD)), radius);
    }

    public double radius() {
        return radius;
    }

    public Vec3 offset() {
        return offset;
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}
