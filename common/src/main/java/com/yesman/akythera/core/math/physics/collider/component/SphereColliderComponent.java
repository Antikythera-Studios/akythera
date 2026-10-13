package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Component of [SphereCollider]
public final class SphereColliderComponent extends ColliderComponent<SphereCollider> {
    /// A world coordinate-system position of the collider
    private Vec3 center;

    /// [SphereCollider]'s radius, grown by [#radiusScale]
    private double radius;

    public SphereColliderComponent(final SphereCollider collider, final @Nullable ComponentAttachable host, final Transform initTransform) {
        super(collider, host);
        applyTransform(initTransform);
    }

    /// Two balls overlap when their centres are within the sum of their radii. Registered for
    /// `SPHERE` x `SPHERE` in [CollisionDetectionLogics].
    public static boolean checkCollisionWithSphere(final SphereColliderComponent sphere1, final SphereColliderComponent sphere2) {
        double reach = sphere1.radius + sphere2.radius;

        return sphere1.center.distanceToSqr(sphere2.center) <= reach * reach;
    }

    /// A ball against a swept segment. Registered for `SPHERE` x `CAPSULE` in
    /// [CollisionDetectionLogics], which is what guarantees the sphere arrives first however the
    /// caller ordered the pair.
    public static boolean checkCollisionWithCapsule(final SphereColliderComponent sphere, final CapsuleColliderComponent capsule) {
        double reach = sphere.radius + capsule.getRadius();

        return VectorUtils.pointToSegmentDistanceSqr(sphere.center, capsule.getEndA(), capsule.getEndB()) <= reach * reach;
    }

    public Vec3 getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public void applyTransform(final Transform transform) {
        center = transform.mul(localTransform().loc());
        radius = getCollider().getRadius() * radiusScale(transform);

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public Vec3 support(final Vec3 direction) {
        return center.add(direction.normalize().scale(radius));
    }

    @Override
    public AABB makeOuterAABB() {
        return new AABB(
            center.x() - radius, center.y() - radius, center.z() - radius,
            center.x() + radius, center.y() + radius, center.z() + radius
        );
    }
}
