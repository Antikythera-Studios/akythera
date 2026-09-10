package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.physics.collider.CapsuleCollider;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.Vec3;

/// Draws [CapsuleCollider] as the two cap rings, four side lines and four hemisphere arcs.
public final class CapsuleColliderRenderer implements ColliderRenderer<CapsuleCollider> {
    @Override
    public void emitWireframe(
        final VertexConsumer consumer, final PoseStack.Pose pose, final CapsuleCollider collider, final int color
    ) {
        Vec3 endA = collider.getLocalEndA();
        Vec3 endB = collider.getLocalEndB();
        double radius = collider.getRadius();
        Vec3 segment = endB.subtract(endA);

        // A capsule of height 0 is a ball, whose segment has no direction to build the ring plane on.
        Vec3 axis = segment.lengthSqr() > 0.0D ? segment.normalize() : MathUtils.YP;
        Vec3 u = VectorUtils.perpendicular(axis);
        Vec3 v = axis.cross(u).normalize();

        ColliderWireframe.arc(consumer, pose, endA, u, v, radius, 0.0D, Math.PI * 2.0D, color);
        ColliderWireframe.arc(consumer, pose, endB, u, v, radius, 0.0D, Math.PI * 2.0D, color);

        for (Vec3 side : new Vec3[] { u, u.scale(-1.0D), v, v.scale(-1.0D) }) {
            Vec3 offset = side.scale(radius);
            ColliderWireframe.line(consumer, pose, endA.add(offset), endB.add(offset), color);
        }

        // Cap arcs: each hemisphere gets one half circle in each of the two planes holding the axis.
        ColliderWireframe.arc(consumer, pose, endB, u, axis, radius, 0.0D, Math.PI, color);
        ColliderWireframe.arc(consumer, pose, endB, v, axis, radius, 0.0D, Math.PI, color);
        ColliderWireframe.arc(consumer, pose, endA, u, axis.scale(-1.0D), radius, 0.0D, Math.PI, color);
        ColliderWireframe.arc(consumer, pose, endA, v, axis.scale(-1.0D), radius, 0.0D, Math.PI, color);
    }
}
