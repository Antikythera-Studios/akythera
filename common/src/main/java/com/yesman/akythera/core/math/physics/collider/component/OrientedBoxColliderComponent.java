package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.OrientedBoxCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Component of [OrientedBoxCollider]
public final class OrientedBoxColliderComponent extends ColliderComponent<OrientedBoxCollider> {
    /// Reflects [OrientedBoxCollider]'s axes, in world coordinate system
    private final Vec3[] axes = new Vec3[3];

    /// A world coordinate-system position of the collider
    private Vec3 center;

    public OrientedBoxColliderComponent(final OrientedBoxCollider collider, final Transform initTransform) {
        super(collider);
        applyTransform(initTransform);
    }

    /// An oriented box around a vanilla [AABB], for testing against one.
    public static OrientedBoxColliderComponent fromAABB(final AABB aabb) {
        OrientedBoxCollider box = new OrientedBoxCollider(
            Transform.identity(),
            new Vec3(aabb.getXsize(), aabb.getYsize(), aabb.getZsize())
        );

        return new OrientedBoxColliderComponent(box, Transform.fromTranslation(aabb.getCenter()));
    }

    public Vec3 getCenter() {
        return center;
    }

    public Vec3 getAxis(final int index) {
        return axes[index];
    }

    @Override
    public void applyTransform(final Transform transform) {
        center = transform.mul(getCollider().getPivotTransform().loc());

        for (int i = 0; i < axes.length; i++) {
            axes[i] = VectorUtils.rotateByQuaternion(transform.rot(), getCollider().getAxis(i));
        }

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public Vec3 support(final Vec3 direction) {
        Vec3 halfExtents = getCollider().getHalfExtents();

        return center
            .add(axes[0].scale(Math.copySign(halfExtents.x(), axes[0].dot(direction))))
            .add(axes[1].scale(Math.copySign(halfExtents.y(), axes[1].dot(direction))))
            .add(axes[2].scale(Math.copySign(halfExtents.z(), axes[2].dot(direction))));
    }

    @Override
    public AABB makeOuterAABB() {
        Vec3 halfExtents = getCollider().getHalfExtents();

        // The box projected onto each world axis: the three half extents, each shrunk by how much its
        // own axis leans away from that world axis.
        double extentX = Math.abs(axes[0].x()) * halfExtents.x()
            + Math.abs(axes[1].x()) * halfExtents.y()
            + Math.abs(axes[2].x()) * halfExtents.z();
        double extentY = Math.abs(axes[0].y()) * halfExtents.x()
            + Math.abs(axes[1].y()) * halfExtents.y()
            + Math.abs(axes[2].y()) * halfExtents.z();
        double extentZ = Math.abs(axes[0].z()) * halfExtents.x()
            + Math.abs(axes[1].z()) * halfExtents.y()
            + Math.abs(axes[2].z()) * halfExtents.z();

        return new AABB(
            center.x() - extentX, center.y() - extentY, center.z() - extentZ,
            center.x() + extentX, center.y() + extentY, center.z() + extentZ
        );
    }
}
