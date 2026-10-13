package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.ConeCollider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Component of [ConeCollider]
public final class ConeColliderComponent extends ColliderComponent<ConeCollider> {
    /// Below this, the search direction is parallel to the cone's axis and the base disc has no
    /// single farthest point—any point on its rim serves, so the centre is returned.
    private static final double PARALLEL_TO_AXIS = 1.0E-12D;

    /// Reflects [ConeCollider]'s apex and base centre, in world coordinate system
    private Vec3 apex;
    private Vec3 baseCenter;

    /// Unit vector from [#baseCenter] to [#apex]
    private Vec3 axis;

    /// [ConeCollider]'s base radius, grown by [#radiusScale]
    private double radius;

    public ConeColliderComponent(final ConeCollider collider, final @Nullable ComponentAttachable host, final Transform initTransform) {
        super(collider, host);
        applyTransform(initTransform);
    }

    public Vec3 getApex() {
        return apex;
    }

    public Vec3 getBaseCenter() {
        return baseCenter;
    }

    public Vec3 getAxis() {
        return axis;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public void applyTransform(final Transform transform) {
        Transform pivot = transformInheritPolicy().inherit(transform, localTransform());
        double halfHeight = getCollider().getHeight() * 0.5D;

        apex = pivot.mul(new Vec3(0.0D, halfHeight, 0.0D));
        baseCenter = pivot.mul(new Vec3(0.0D, -halfHeight, 0.0D));
        axis = apex.subtract(baseCenter).normalize();
        radius = getCollider().getRadius() * radiusScale(transform);

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    /// A cone is the convex hull of its apex and its base disc, so its farthest point is whichever of
    /// those two is farther.
    @Override
    public Vec3 support(final Vec3 direction) {
        Vec3 radial = direction.subtract(axis.scale(direction.dot(axis)));
        Vec3 basePoint = radial.lengthSqr() > PARALLEL_TO_AXIS
            ? baseCenter.add(radial.normalize().scale(radius))
            : baseCenter;

        return direction.dot(apex) >= direction.dot(basePoint) ? apex : basePoint;
    }

    @Override
    public AABB makeOuterAABB() {
        // The base disc projected onto a world axis: the full radius when the disc faces it head on,
        // shrinking to zero as the cone's axis turns to meet it.
        double extentX = radius * Math.sqrt(Math.max(0.0D, 1.0D - axis.x() * axis.x()));
        double extentY = radius * Math.sqrt(Math.max(0.0D, 1.0D - axis.y() * axis.y()));
        double extentZ = radius * Math.sqrt(Math.max(0.0D, 1.0D - axis.z() * axis.z()));

        return new AABB(
            Math.min(apex.x(), baseCenter.x() - extentX),
            Math.min(apex.y(), baseCenter.y() - extentY),
            Math.min(apex.z(), baseCenter.z() - extentZ),
            Math.max(apex.x(), baseCenter.x() + extentX),
            Math.max(apex.y(), baseCenter.y() + extentY),
            Math.max(apex.z(), baseCenter.z() + extentZ)
        );
    }
}
