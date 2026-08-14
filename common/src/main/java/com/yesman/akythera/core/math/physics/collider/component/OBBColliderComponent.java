package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.OBBCollider;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Component of [OBBCollider]
public final class OBBColliderComponent extends ColliderComponent<OBBCollider> {
    /// Reflects [OBBCollider]'s upward vertices, in world coordinate system
    final Vec3[] upwardVertices = new Vec3[4];

    /// Reflects [OBBCollider]'s normals, in world coordinate system
    final Vec3[] normals = new Vec3[3];

    /// A world coordinate-system position of the collider
    Vec3 center;

    public OBBColliderComponent(final OBBCollider collider, final Transform initTransform) {
        super(collider);
        applyTransform(initTransform);
    }

    public Vec3 getCenter() {
        return center;
    }

    @Override
    public void applyTransform(final Transform transform) {
        center = transform.mul(getCollider().getPivotTransform().loc());

        for (int i = 0; i < upwardVertices.length; i++) {
            upwardVertices[i] = transform.mulDirection(getCollider().getUpwardVertex(i));
        }

        for (int i = 0; i < normals.length; i++) {
            normals[i] = VectorUtils.rotateByQuaternion(transform.rot(), getCollider().getNormal(i));
        }

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public AABB makeOuterAABB() {
        // Make a covering AABB by each edge position
        double minX = MathUtils.least(
            center.x() - Math.abs(upwardVertices[0].x()),
            center.x() - Math.abs(upwardVertices[1].x()),
            center.x() - Math.abs(upwardVertices[2].x()),
            center.x() - Math.abs(upwardVertices[3].x())
        );

        double minY = MathUtils.least(
            center.y() - Math.abs(upwardVertices[0].y()),
            center.y() - Math.abs(upwardVertices[1].y()),
            center.y() - Math.abs(upwardVertices[2].y()),
            center.y() - Math.abs(upwardVertices[3].y())
        );

        double minZ = MathUtils.least(
            center.z() - Math.abs(upwardVertices[0].z()),
            center.z() - Math.abs(upwardVertices[1].z()),
            center.z() - Math.abs(upwardVertices[2].z()),
            center.z() - Math.abs(upwardVertices[3].z())
        );

        double maxX = MathUtils.greatest(
            center.x() + Math.abs(upwardVertices[0].x()),
            center.x() + Math.abs(upwardVertices[1].x()),
            center.x() + Math.abs(upwardVertices[2].x()),
            center.x() + Math.abs(upwardVertices[3].x())
        );

        double maxY = MathUtils.greatest(
            center.y() + Math.abs(upwardVertices[0].y()),
            center.y() + Math.abs(upwardVertices[1].y()),
            center.y() + Math.abs(upwardVertices[2].y()),
            center.y() + Math.abs(upwardVertices[3].y())
        );

        double maxZ = MathUtils.greatest(
            center.z() + Math.abs(upwardVertices[0].z()),
            center.z() + Math.abs(upwardVertices[1].z()),
            center.z() + Math.abs(upwardVertices[2].z()),
            center.z() + Math.abs(upwardVertices[3].z())
        );

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /// Separating axis test against another box. Registered for
    /// `ORIENTATED_BOUNDING_BOX` x `ORIENTATED_BOUNDING_BOX` in [CollisionDetectionLogics].
    public static boolean checkCollisionWithOBB(final OBBColliderComponent obb1, final OBBColliderComponent obb2) {
        // Centers already carry the pivot translation, folded in by `applyTransform`.
        Vec3 toOpponent = obb1.center.subtract(obb2.center);

        for (Vec3 separateAxis : obb1.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, obb1, obb2)) {
                return false;
            }
        }

        for (Vec3 separateAxis : obb2.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, obb1, obb2)) {
                return false;
            }
        }

        return true;
    }

    /// Creates an OBB collider component from [AABB], for testing against a vanilla box.
    public static OBBColliderComponent fromAABB(final AABB aabb) {
        double width = (aabb.maxX - aabb.minX) * 0.5D;
        double height = (aabb.maxY - aabb.minY) * 0.5D;
        double depth = (aabb.maxZ - aabb.minZ) * 0.5D;
        OBBCollider obb = new OBBCollider(Transform.identity(), new Vec3(width, height, depth), 1);

        return new OBBColliderComponent(obb, Transform.fromTranslation(aabb.getCenter()));
    }

    private static boolean checkSeparateAxisOverlap(
        final Vec3 axis, final Vec3 toOpponent, final OBBColliderComponent obb1, final OBBColliderComponent obb2
    ) {
        // `Vec3` is immutable, so the flipped axis is the value `scale` returns.
        Vec3 separateAxis = axis.dot(toOpponent) < 0.0D ? axis.scale(-1.0D) : axis;
        Vec3 maxProj1 = null, maxProj2 = null;
        double maxDot1 = -1, maxDot2 = -1;

        for (Vec3 vertexVector : obb1.upwardVertices) {
            Vec3 temp = separateAxis.dot(vertexVector) > 0.0D ? vertexVector : vertexVector.scale(-1.0D);
            double dot = separateAxis.dot(temp);

            if (dot > maxDot1) {
                maxDot1 = dot;
                maxProj1 = temp;
            }
        }

        for (Vec3 vertexVector : obb2.upwardVertices) {
            Vec3 temp = separateAxis.dot(vertexVector) > 0.0D ? vertexVector : vertexVector.scale(-1.0D);
            double dot = separateAxis.dot(temp);

            if (dot > maxDot2) {
                maxDot2 = dot;
                maxProj2 = temp;
            }
        }

        return VectorUtils.projectVector(toOpponent, separateAxis).length()
            < VectorUtils.projectVector(maxProj1, separateAxis).length()
            + VectorUtils.projectVector(maxProj2, separateAxis).length();
    }
}
