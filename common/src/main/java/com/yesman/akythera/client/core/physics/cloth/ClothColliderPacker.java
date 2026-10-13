package com.yesman.akythera.client.core.physics.cloth;

import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.core.math.physics.collider.CapsuleCollider;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.OrientedBoxCollider;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

/// Writes one [Collider] as the solver's `Collider` struct, [#STRIDE] bytes. Registered per collider
/// type on [ClientComponentsFactory].
@FunctionalInterface
public interface ClothColliderPacker<T extends Collider> {
    /// Bytes per packed collider: six `vec4`s.
    int STRIDE = 96;

    float SHAPE_SPHERE = 0.0F;
    float SHAPE_CAPSULE = 1.0F;
    float SHAPE_BOX = 2.0F;

    ClothColliderPacker<SphereCollider> SPHERE = (sphere, toSimulation, buffer, offset) -> {
        putVector(buffer, offset, SHAPE_SPHERE, (float) sphere.getRadius(), 0.0F);
        putPoint(buffer, offset + 16, toSimulation, sphere.getPivotTransform().loc());
    };

    ClothColliderPacker<CapsuleCollider> CAPSULE = (capsule, toSimulation, buffer, offset) -> {
        putVector(buffer, offset, SHAPE_CAPSULE, (float) capsule.getRadius(), 0.0F);
        putPoint(buffer, offset + 16, toSimulation, capsule.getLocalEndA());
        putPoint(buffer, offset + 32, toSimulation, capsule.getLocalEndB());
    };

    ClothColliderPacker<OrientedBoxCollider> ORIENTED_BOX = (box, toSimulation, buffer, offset) -> {
        Vec3 halfExtents = box.getHalfExtents();

        putVector(buffer, offset, SHAPE_BOX, 0.0F, 0.0F);
        putPoint(buffer, offset + 16, toSimulation, box.getPivotTransform().loc());
        putVector(buffer, offset + 32, (float) halfExtents.x, (float) halfExtents.y, (float) halfExtents.z);

        for (int axis = 0; axis < 3; axis++) {
            Vec3 local = box.getAxis(axis);
            Vector3f direction = toSimulation.transformDirection(new Vector3f((float) local.x, (float) local.y, (float) local.z)).normalize();

            putVector(buffer, offset + 48 + axis * 16, direction.x, direction.y, direction.z);
        }
    };

    /// `colliderToSimulation` maps the collider's own (bone) space into simulation space.
    void pack(final T collider, final Matrix4f colliderToSimulation, final ByteBuffer buffer, final int offset);

    private static void putPoint(final ByteBuffer buffer, final int offset, final Matrix4f toSimulation, final Vec3 local) {
        Vector3f point = toSimulation.transformPosition(new Vector3f((float) local.x, (float) local.y, (float) local.z));

        putVector(buffer, offset, point.x, point.y, point.z);
    }

    private static void putVector(final ByteBuffer buffer, final int offset, final float x, final float y, final float z) {
        buffer.putFloat(offset, x);
        buffer.putFloat(offset + 4, y);
        buffer.putFloat(offset + 8, z);
        buffer.putFloat(offset + 12, 0.0F);
    }
}
