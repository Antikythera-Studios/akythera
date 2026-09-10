package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.physics.collider.ConeCollider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.Vec3;

/// Draws [ConeCollider] as its base ring and four slant lines to the apex.
public final class ConeColliderRenderer implements ColliderRenderer<ConeCollider> {
    /// Slant lines drawn from the base ring to the apex, spaced evenly around it.
    private static final int SLANT_LINES = 4;

    @Override
    public void emitWireframe(
        final VertexConsumer consumer, final PoseStack.Pose pose, final ConeCollider collider, final int color
    ) {
        Vec3 apex = collider.getLocalApex();
        Vec3 baseCenter = collider.getLocalBaseCenter();
        double radius = collider.getRadius();
        Vec3 axis = apex.subtract(baseCenter).normalize();
        Vec3 u = VectorUtils.perpendicular(axis);
        Vec3 v = axis.cross(u).normalize();

        ColliderWireframe.arc(consumer, pose, baseCenter, u, v, radius, 0.0D, Math.PI * 2.0D, color);

        for (int i = 0; i < SLANT_LINES; i++) {
            double angle = Math.PI * 2.0D * i / SLANT_LINES;
            ColliderWireframe.line(
                consumer, pose, ColliderWireframe.arcPoint(baseCenter, u, v, radius, angle), apex, color
            );
        }
    }
}
