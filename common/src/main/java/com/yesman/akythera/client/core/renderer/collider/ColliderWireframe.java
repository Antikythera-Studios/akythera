package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/// Line primitives every [ColliderRenderer] draws with.
public final class ColliderWireframe {
    private ColliderWireframe() {}

    /// Segments a full circle is cut into. Every arc is cut at this density, so a half circle takes
    /// half as many.
    public static final int CIRCLE_SEGMENTS = 24;

    private static final float LINE_WIDTH = 2.0F;

    public static void line(
        final VertexConsumer consumer, final PoseStack.Pose pose, final Vec3 from, final Vec3 to, final int color
    ) {
        Vec3 normal = to.subtract(from).normalize();

        consumer.addVertex(pose, (float) from.x(), (float) from.y(), (float) from.z())
            .setColor(color)
            .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z())
            .setLineWidth(LINE_WIDTH);
        consumer.addVertex(pose, (float) to.x(), (float) to.y(), (float) to.z())
            .setColor(color)
            .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z())
            .setLineWidth(LINE_WIDTH);
    }

    /// An arc of `radius` around `center`, swept from `fromAngle` to `toAngle` radians in the plane
    /// `axisU` x `axisV`, starting on `axisU`. Both axes are taken as unit and perpendicular.
    public static void arc(
        final VertexConsumer consumer,
        final PoseStack.Pose pose,
        final Vec3 center,
        final Vec3 axisU,
        final Vec3 axisV,
        final double radius,
        final double fromAngle,
        final double toAngle,
        final int color
    ) {
        int segments = Math.max(1, (int) Math.ceil(Math.abs(toAngle - fromAngle) / (Math.PI * 2.0D) * CIRCLE_SEGMENTS));
        double step = (toAngle - fromAngle) / segments;
        Vec3 previous = arcPoint(center, axisU, axisV, radius, fromAngle);

        for (int i = 1; i <= segments; i++) {
            Vec3 current = arcPoint(center, axisU, axisV, radius, fromAngle + step * i);
            line(consumer, pose, previous, current, color);
            previous = current;
        }
    }

    public static Vec3 arcPoint(
        final Vec3 center, final Vec3 axisU, final Vec3 axisV, final double radius, final double angle
    ) {
        return center
            .add(axisU.scale(Math.cos(angle) * radius))
            .add(axisV.scale(Math.sin(angle) * radius));
    }
}
