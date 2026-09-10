package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Component of [SphereCollider]
public final class SphereColliderComponent extends ColliderComponent<SphereCollider> {
    /// A world coordinate-system position of the collider
    private Vec3 center;

    public SphereColliderComponent(final SphereCollider collider, final Transform initTransform) {
        super(collider);
        applyTransform(initTransform);
    }

    /// Two balls overlap when their centres are within the sum of their radii. Registered for
    /// `SPHERE` x `SPHERE` in [CollisionDetectionLogics].
    public static boolean checkCollisionWithSphere(final SphereColliderComponent sphere1, final SphereColliderComponent sphere2) {
        double reach = sphere1.getCollider().getRadius() + sphere2.getCollider().getRadius();

        return sphere1.center.distanceToSqr(sphere2.center) <= reach * reach;
    }

    /// A ball against a swept segment. Registered for `SPHERE` x `CAPSULE` in
    /// [CollisionDetectionLogics], which is what guarantees the sphere arrives first however the
    /// caller ordered the pair.
    public static boolean checkCollisionWithCapsule(final SphereColliderComponent sphere, final CapsuleColliderComponent capsule) {
        double reach = sphere.getCollider().getRadius() + capsule.getCollider().getRadius();

        return VectorUtils.pointToSegmentDistanceSqr(sphere.center, capsule.getEndA(), capsule.getEndB()) <= reach * reach;
    }

    public Vec3 getCenter() {
        return center;
    }

    @Override
    public void applyTransform(final Transform transform) {
        center = transform.mul(getCollider().getPivotTransform().loc());

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public Vec3 support(final Vec3 direction) {
        return center.add(direction.normalize().scale(getCollider().getRadius()));
    }

    @Override
    public AABB makeOuterAABB() {
        double radius = getCollider().getRadius();

        return new AABB(
            center.x() - radius, center.y() - radius, center.z() - radius,
            center.x() + radius, center.y() + radius, center.z() + radius
        );
    }
}
