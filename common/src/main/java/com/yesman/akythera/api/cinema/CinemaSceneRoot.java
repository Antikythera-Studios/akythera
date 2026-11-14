package com.yesman.akythera.api.cinema;

import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/// Where a playing [CinemaScene] has its origin.
public sealed interface CinemaSceneRoot {
    /// The scene's space `partialTick` after the last tick, in world space: origin on the root, -Z forward, +X right.
    Transform transform(float partialTick);

    /// The scene's space at `position`, in world space, facing `yRot`, in degrees.
    static Transform transformAt(final Vec3 position, final float yRot) {
        return new Transform(position, new Quaterniond().rotationY((180.0F - yRot) * Mth.DEG_TO_RAD), Transform.IDENTITY.sca());
    }

    /// A fixed place.
    ///
    /// @param position in world space
    /// @param yRot     where the scene faces, in degrees
    record At(Vec3 position, float yRot) implements CinemaSceneRoot {
        @Override
        public Transform transform(final float partialTick) {
            return transformAt(position, yRot);
        }
    }

    /// An entity, at its position and body yaw.
    record On(Entity entity) implements CinemaSceneRoot {
        @Override
        public Transform transform(final float partialTick) {
            float yRot = entity instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : entity.getYRot(partialTick);

            return transformAt(entity.getPosition(partialTick), yRot);
        }
    }
}
