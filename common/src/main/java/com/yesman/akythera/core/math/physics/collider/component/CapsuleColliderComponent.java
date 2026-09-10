package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CapsuleCollider;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Component of [CapsuleCollider]
public final class CapsuleColliderComponent extends ColliderComponent<CapsuleCollider> {
    /// Reflects [CapsuleCollider]'s hemisphere centres, in world coordinate system
    private Vec3 endA;
    private Vec3 endB;

    public CapsuleColliderComponent(final CapsuleCollider collider, final Transform initTransform) {
        super(collider);
        applyTransform(initTransform);
    }

    /// Two swept segments overlap when the segments are within the sum of their radii. Registered for
    /// `CAPSULE` x `CAPSULE` in [CollisionDetectionLogics].
    public static boolean checkCollisionWithCapsule(final CapsuleColliderComponent capsule1, final CapsuleColliderComponent capsule2) {
        double reach = capsule1.getCollider().getRadius() + capsule2.getCollider().getRadius();

        return VectorUtils.segmentDistanceSqr(capsule1.endA, capsule1.endB, capsule2.endA, capsule2.endB) <= reach * reach;
    }

    public Vec3 getEndA() {
        return endA;
    }

    public Vec3 getEndB() {
        return endB;
    }

    @Override
    public void applyTransform(final Transform transform) {
        endA = transform.mul(getCollider().getLocalEndA());
        endB = transform.mul(getCollider().getLocalEndB());

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public Vec3 support(final Vec3 direction) {
        Vec3 farthestEnd = direction.dot(endA) >= direction.dot(endB) ? endA : endB;

        return farthestEnd.add(direction.normalize().scale(getCollider().getRadius()));
    }

    @Override
    public AABB makeOuterAABB() {
        double radius = getCollider().getRadius();

        return new AABB(
            Math.min(endA.x(), endB.x()) - radius,
            Math.min(endA.y(), endB.y()) - radius,
            Math.min(endA.z(), endB.z()) - radius,
            Math.max(endA.x(), endB.x()) + radius,
            Math.max(endA.y(), endB.y()) + radius,
            Math.max(endA.z(), endB.z()) + radius
        );
    }
}
