package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.physics.collider.OrientedBoxCollider;
import net.minecraft.world.phys.Vec3;

/// Draws [OrientedBoxCollider] as its twelve edges.
public final class OrientedBoxColliderRenderer implements ColliderRenderer<OrientedBoxCollider> {
    @Override
    public void emitWireframe(
        final VertexConsumer consumer, final PoseStack.Pose pose, final OrientedBoxCollider collider, final int color
    ) {
        Vec3 halfExtents = collider.getHalfExtents();
        Vec3 center = collider.getPivotTransform().loc();
        Vec3 x = collider.getAxis(0).scale(halfExtents.x());
        Vec3 y = collider.getAxis(1).scale(halfExtents.y());
        Vec3 z = collider.getAxis(2).scale(halfExtents.z());
        Vec3[] corners = new Vec3[8];

        // Bit 0 is the sign along x, bit 1 along y, bit 2 along z, so two corners share an edge
        // exactly when their indices differ in one bit.
        for (int i = 0; i < corners.length; i++) {
            corners[i] = center
                .add((i & 1) == 0 ? x.scale(-1.0D) : x)
                .add((i & 2) == 0 ? y.scale(-1.0D) : y)
                .add((i & 4) == 0 ? z.scale(-1.0D) : z);
        }

        for (int i = 0; i < corners.length; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                // Only the edge running toward the set bit, so each of the twelve is drawn once.
                if ((i & bit) == 0) {
                    ColliderWireframe.line(consumer, pose, corners[i], corners[i | bit], color);
                }
            }
        }
    }
}
