package com.yesman.akythera.core.math.physics.collider.instance;

import com.google.common.collect.ImmutableMap;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.OBBCollider;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/// Instance of [OBBCollider]
public final class OBBColliderInstance extends ColliderInstance<OBBCollider> {
    public static final Map<Class<? extends Collider>, CollisionCheck<OBBCollider, ? extends Collider>> SUPPORTED_COLLIDE_CHECKS;

    static {
        SUPPORTED_COLLIDE_CHECKS = ImmutableMap.<Class<? extends Collider>, CollisionCheck<OBBCollider, ? extends Collider>>builder()
            .put(OBBCollider.class, OBBColliderInstance::checkCollisionWithOBB)
            .build();
    }

    /// Reflects [OBBCollider#upwardVertices], in world coordinate system
    final Vec3[] upwardVertices = new Vec3[4];

    /// Reflects [OBBCollider#normals], in world coordinate system
    final Vec3[] normals = new Vec3[3];

    /// A world coordinate-system position of the collider
    Vec3 center;

    public OBBColliderInstance(OBBCollider collider, Transform transform) {
        super(collider, transform);
    }

    public Vec3 getCenter() {
        return this.center;
    }

    @Override
    public void applyTransform(Transform transform) {
        this.center = transform.mul(this.getCollider().getPivotTransform().loc());

        for (int i = 0; i < this.upwardVertices.length; i++) {
            this.upwardVertices[i] = transform.mulDirection(this.getCollider().getUpwardVertex(i));
        }

        for (int i = 0; i < this.normals.length; i++) {
            this.normals[i] = VectorUtils.rotateByQuaternion(transform.rot(), this.getCollider().getNormal(i));
        }

        // Update outer AABB after updating the world coordinate
        this.outerAABB = this.makeOuterAABB();
    }

    @Override
    public AABB makeOuterAABB() {
        // Make a covering AABB by each edge position
        double minX = MathUtils.least(
            this.center.x() - Math.abs(this.upwardVertices[0].x()),
            this.center.x() - Math.abs(this.upwardVertices[1].x()),
            this.center.x() - Math.abs(this.upwardVertices[2].x()),
            this.center.x() - Math.abs(this.upwardVertices[3].x())
        );

        double minY = MathUtils.least(
            this.center.y() - Math.abs(this.upwardVertices[0].y()),
            this.center.y() - Math.abs(this.upwardVertices[1].y()),
            this.center.y() - Math.abs(this.upwardVertices[2].y()),
            this.center.y() - Math.abs(this.upwardVertices[3].y())
        );

        double minZ = MathUtils.least(
            this.center.z() - Math.abs(this.upwardVertices[0].z()),
            this.center.z() - Math.abs(this.upwardVertices[1].z()),
            this.center.z() - Math.abs(this.upwardVertices[2].z()),
            this.center.z() - Math.abs(this.upwardVertices[3].z())
        );

        double maxX = MathUtils.greatest(
            this.center.x() + Math.abs(this.upwardVertices[0].x()),
            this.center.x() + Math.abs(this.upwardVertices[1].x()),
            this.center.x() + Math.abs(this.upwardVertices[2].x()),
            this.center.x() + Math.abs(this.upwardVertices[3].x())
        );

        double maxY = MathUtils.greatest(
            this.center.y() + Math.abs(this.upwardVertices[0].y()),
            this.center.y() + Math.abs(this.upwardVertices[1].y()),
            this.center.y() + Math.abs(this.upwardVertices[2].y()),
            this.center.y() + Math.abs(this.upwardVertices[3].y())
        );

        double maxZ = MathUtils.greatest(
            this.center.z() + Math.abs(this.upwardVertices[0].z()),
            this.center.z() + Math.abs(this.upwardVertices[1].z()),
            this.center.z() + Math.abs(this.upwardVertices[2].z()),
            this.center.z() + Math.abs(this.upwardVertices[3].z())
        );

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public Map<Class<? extends Collider>, CollisionCheck<OBBCollider, ? extends Collider>> getSupportedCollideChecks() {
        return SUPPORTED_COLLIDE_CHECKS;
    }

    public static boolean checkCollisionWithOBB(ColliderInstance<OBBCollider> collider1, ColliderInstance<?> collider2) {
        OBBColliderInstance asOBB1 = (OBBColliderInstance)collider1;
        OBBColliderInstance asOBB2 = (OBBColliderInstance)collider2;
        Vec3 toOpponent =
            asOBB1.center.add(asOBB1.getCollider().getPivotTransform().loc())
                .subtract(asOBB2.center.add(asOBB2.getCollider().getPivotTransform().loc()));

        for (Vec3 separateAxis : asOBB1.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, asOBB1, asOBB2)) {
                return false;
            }
        }

        for (Vec3 separateAxis : asOBB2.normals) {
            if (!checkSeparateAxisOverlap(separateAxis, toOpponent, asOBB1, asOBB2)) {
                return false;
            }
        }

        return true;
    }

    /// Creates a OBB collider instance from [AABB]
    public static OBBColliderInstance fromAABB(AABB aabb) {
        double width = (aabb.maxX - aabb.minX) * 0.5D;
        double height = (aabb.maxY - aabb.minY) * 0.5D;
        double depth = (aabb.maxZ - aabb.minZ) * 0.5D;
        OBBCollider obb = new OBBCollider(Transform.identity(), new Vec3(width, height, depth), 1);
        return new OBBColliderInstance(obb, Transform.fromTranslation(aabb.getCenter()));
    }

    private static boolean checkSeparateAxisOverlap(Vec3 separateAxis, Vec3 toOpponent, OBBColliderInstance obb1, OBBColliderInstance obb2) {
        Vec3 maxProj1 = null, maxProj2 = null;
        double maxDot1 = -1, maxDot2 = -1;

        if (separateAxis.dot(toOpponent) < 0.0F) {
            separateAxis.scale(-1.0D);
        }

        for (Vec3 vertexVector : obb1.upwardVertices) {
            Vec3 temp = separateAxis.dot(vertexVector) > 0.0F ? vertexVector : vertexVector.scale(-1.0D);
            double dot = separateAxis.dot(temp);

            if (dot > maxDot1) {
                maxDot1 = dot;
                maxProj1 = temp;
            }
        }

        for (Vec3 vertexVector : obb2.upwardVertices) {
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
