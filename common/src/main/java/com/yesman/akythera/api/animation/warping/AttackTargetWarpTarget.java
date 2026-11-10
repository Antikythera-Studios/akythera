package com.yesman.akythera.api.animation.warping;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Warps to the front of [EntityPatch#getTarget]: the target's position, moved toward the owner until the two bounding
/// boxes touch. Warps nothing while the owner has no target.
public record AttackTargetWarpTarget() implements MotionWarpTargetBlueprint, MotionWarpTarget {
    public static final AttackTargetWarpTarget INSTANCE = new AttackTargetWarpTarget();

    public static final MapCodec<AttackTargetWarpTarget> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public @Nullable Vec3 find(final EntityPatch<?> owner) {
        LivingEntity target = owner.getTarget();

        if (target == null) return null;

        Vec3 toOwner = owner.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);

        if (toOwner.lengthSqr() < 1.0E-6D) return target.position();

        return target.position().add(toOwner.normalize().scale((target.getBbWidth() + owner.getBbWidth()) * 0.5D));
    }

    @Override
    public MotionWarpTarget instantiate(final EntityPatch<?> owner) {
        return this;
    }

    @Override
    public MapCodec<AttackTargetWarpTarget> mapCodec() {
        return MAP_CODEC;
    }
}
