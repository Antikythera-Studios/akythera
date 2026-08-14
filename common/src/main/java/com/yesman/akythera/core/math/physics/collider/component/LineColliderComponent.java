package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.LineCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Component of [LineCollider]
public final class LineColliderComponent extends ColliderComponent<LineCollider> {
    /// Reflects [LineCollider]'s pivot point, in world coordinate system
    private Vec3 point;

    /// A vector from [#point] to another point of line
    private Vec3 towardEdge;

    /// Normal vectors
    private final Vec3[] normals = new Vec3[2];

    public LineColliderComponent(final LineCollider collider, final Transform initTransform) {
        super(collider);
        applyTransform(initTransform);
    }

    @Override
    public void applyTransform(final Transform transform) {
        point = transform.mul(getCollider().getPivotTransform().loc());
        towardEdge = transform.mulDirection(new Vec3(0.0D, 0.0D, -1.0D));
        normals[0] = VectorUtils.rotateByQuaternion(transform.rot(), 1.0D, 0.0D, 0.0D);
        normals[1] = VectorUtils.rotateByQuaternion(transform.rot(), 0.0D, 1.0D, 0.0D);
        // No need to maintain Z normal since the length is always 0

        // Update outer AABB after updating the world coordinate
        outerAABB = makeOuterAABB();
    }

    @Override
    public AABB makeOuterAABB() {
        double minX = Math.min(point.x(), point.x() + towardEdge.x());
        double minY = Math.min(point.y(), point.y() + towardEdge.y());
        double minZ = Math.min(point.z(), point.z() + towardEdge.z());
        double maxX = Math.max(point.x(), point.x() + towardEdge.x());
        double maxY = Math.max(point.y(), point.y() + towardEdge.y());
        double maxZ = Math.max(point.z(), point.z() + towardEdge.z());

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /// Separating axis test of a line against a box. Registered for `LINE` x
    /// `ORIENTATED_BOUNDING_BOX` in [CollisionDetectionLogics], which is what guarantees the line
    /// arrives first however the caller ordered the pair.
    public static boolean checkCollisionWithOBB(final LineColliderComponent line, final OBBColliderComponent obb) {
        // Both points already carry their pivot translation, folded in by `applyTransform`.
        Vec3 toOpponent = line.point.subtract(obb.getCenter());

        for (Vec3 separateAxis : line.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, line, obb)) {
                return false;
            }
        }

        for (Vec3 separateAxis : obb.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, line, obb)) {
                return false;
            }
        }

        return true;
    }

    private static boolean checkSeparateAxisOverlap(
        final Vec3 axis, final Vec3 toOpponent, final LineColliderComponent line, final OBBColliderComponent obb
    ) {
        // `Vec3` is immutable, so the flipped axis is the value `scale` returns. The early-out below
        // reads its sign.
        Vec3 separateAxis = axis.dot(toOpponent) < 0.0D ? axis.scale(-1.0D) : axis;

        if (separateAxis.dot(line.towardEdge) <= 0.0D) {
            return false;
        }

        Vec3 maxProj = null;
        double maxDot = -1;

        for (Vec3 vertexVector : obb.upwardVertices) {
            Vec3 temp = separateAxis.dot(vertexVector) > 0.0D ? vertexVector : vertexVector.scale(-1.0D);
            double dot = separateAxis.dot(temp);

            if (dot > maxDot) {
                maxDot = dot;
                maxProj = temp;
            }
        }

        return VectorUtils.projectVector(toOpponent, separateAxis).length()
            < VectorUtils.projectVector(line.towardEdge, separateAxis).length()
            + VectorUtils.projectVector(maxProj, separateAxis).length();
    }
}
