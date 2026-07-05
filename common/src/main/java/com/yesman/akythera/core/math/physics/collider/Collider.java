package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.instance.ColliderInstance;
import net.minecraft.world.phys.AABB;

/// Detects whether two objects are overlapped in a 3D space.
///
/// Compatible with Minecraft's collider, [AABB].
///
/// Beware colliders are not directly used to check collision.
/// [ColliderInstance], the stateful version of colliders, is
/// in charge of detecting intersects of objects in a world.
public abstract class Collider {
    public static final Codec<? extends Collider> CODEC =
        ColliderType.ENUM_MANAGER.getNameCodec()
            .dispatch(
                Collider::type,
                ColliderType::mapCodec
            );

    /// Cast to captured type of [#CODEC]
    @SuppressWarnings("unchecked")
    public static <T extends Collider> T cast(Collider collider) {
        return (T)collider;
    }

    /// Transform of a pivot point
    ///
    /// Kept the transform info for [Codec], while most collider
    /// components are already applied by pivot transform.
    private final Transform pivotTransform;

    /// Creates interpolated colliders between previous and next tick
    private final int interpolations;

    public Collider(Transform pivotTransform, int interpolations) {
        this.pivotTransform = pivotTransform;
        this.interpolations = interpolations;
    }

    /// Creates a stateful collider instance
    public abstract ColliderInstance<?> createInstance(Transform transform);

    public abstract ColliderType type();

    public Transform getPivotTransform() {
        return this.pivotTransform;
    }

    public int getInterpolations() {
        return this.interpolations;
    }
}
