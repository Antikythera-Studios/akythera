package com.yesman.akythera.core.math.physics.collider.instance;

import com.google.common.collect.ImmutableMap;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.LineCollider;
import com.yesman.akythera.core.math.physics.collider.OBBCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class LineColliderInstance extends ColliderInstance<LineCollider> {
    public static final Map<Class<? extends Collider>, CollisionCheck<LineCollider, ? extends Collider>> SUPPORTED_COLLIDE_CHECKS;

    static {
        SUPPORTED_COLLIDE_CHECKS = ImmutableMap.<Class<? extends Collider>, CollisionCheck<LineCollider, ? extends Collider>>builder()
            .put(OBBCollider.class, LineColliderInstance::checkCollisionWithOBB)
            .build();
    }

    /// Reflects [LineCollider#point], in world coordinate system
    private Vec3 point;
    /// A vector from [#point] to another point of line
    private Vec3 towardEdge;
    /// Normal vectors
    private final Vec3[] normals = new Vec3[2];

    public LineColliderInstance(LineCollider collider, Transform initTransform) {
        super(collider, initTransform);
    }

    @Override
    public void applyTransform(Transform transform) {
        this.point = transform.mul(this.getCollider().getPivotTransform().loc());
        this.towardEdge = transform.mulDirection(new Vec3(0.0D, 0.0D, -1.0D));
        this.normals[0] = VectorUtils.rotateByQuaternion(transform.rot(), 1.0D, 0.0D, 0.0D);
        this.normals[1] = VectorUtils.rotateByQuaternion(transform.rot(), 0.0D, 1.0D, 0.0D);
        // No need to maintain Z normal since the length is always 0

        // Update outer AABB after updating the world coordinate
        this.outerAABB = this.makeOuterAABB();
    }

    @Override
    public AABB makeOuterAABB() {
        double minX = Math.min(this.point.x(), this.point.x() + this.towardEdge.x());
        double minY = Math.min(this.point.y(), this.point.y() + this.towardEdge.y());
        double minZ = Math.min(this.point.z(), this.point.z() + this.towardEdge.z());
        double maxX = Math.max(this.point.x(), this.point.x() + this.towardEdge.x());
        double maxY = Math.max(this.point.y(), this.point.y() + this.towardEdge.y());
        double maxZ = Math.max(this.point.z(), this.point.z() + this.towardEdge.z());

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public Map<Class<? extends Collider>, CollisionCheck<LineCollider, ? extends Collider>> getSupportedCollideChecks() {
        return SUPPORTED_COLLIDE_CHECKS;
    }

    public static boolean checkCollisionWithOBB(ColliderInstance<LineCollider> collider1, ColliderInstance<?> collider2) {
        LineColliderInstance asLine = (LineColliderInstance)collider1;
        OBBColliderInstance asOBB = (OBBColliderInstance)collider2;
        Vec3 toOpponent = asLine.point.subtract(asOBB.getCenter().add(asOBB.getCollider().getPivotTransform().loc()));

        for (Vec3 separateAxis : asLine.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, asLine, asOBB)) {
                return false;
            }
        }

        for (Vec3 separateAxis : asOBB.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, asLine, asOBB)) {
                return false;
            }
        }

        return true;
    }

    public static boolean checkSeparateAxisOverlap(Vec3 separateAxis, Vec3 toOpponent, LineColliderInstance line, OBBColliderInstance obb) {
        Vec3 maxProj1 = null, maxProj2 = null;
        double maxDot1 = -1, maxDot2 = -1;

        if (separateAxis.dot(toOpponent) < 0.0F) {
            separateAxis.scale(-1.0D);
        }

        if (separateAxis.dot(line.towardEdge) <= 0.0F) {
            return false;
        }

        {
            double dot = separateAxis.dot(line.towardEdge);

            if (dot > maxDot1) {
                maxDot1 = dot;
                maxProj1 = line.towardEdge;
            }
        }

        for (Vec3 vertexVector : obb.upwardVertices) {
            Vec3 temp = separateAxis.dot(vertexVector) > 0.0F ? vertexVector : vertexVector.scale(-1.0D);
            double dot = separateAxis.dot(temp);

            if (dot > maxDot2) {
                maxDot2 = dot;
                maxProj2 = temp;
            }
        }

        return VectorUtils.projectVector(toOpponent, separateAxis).length() < VectorUtils.projectVector(maxProj1, separateAxis).length() + VectorUtils.projectVector(maxProj2, separateAxis).length();
    }
}
