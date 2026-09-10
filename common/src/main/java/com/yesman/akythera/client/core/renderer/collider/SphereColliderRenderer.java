package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akythera.core.util.helper.MathUtils;
import net.minecraft.world.phys.Vec3;

/// Draws [SphereCollider] as its three great circles.
public final class SphereColliderRenderer implements ColliderRenderer<SphereCollider> {
    @Override
    public void emitWireframe(
        final VertexConsumer consumer, final PoseStack.Pose pose, final SphereCollider collider, final int color
    ) {
        Vec3 center = collider.getPivotTransform().loc();
        double radius = collider.getRadius();

        ColliderWireframe.arc(consumer, pose, center, MathUtils.XP, MathUtils.YP, radius, 0.0D, Math.PI * 2.0D, color);
        ColliderWireframe.arc(consumer, pose, center, MathUtils.YP, MathUtils.ZP, radius, 0.0D, Math.PI * 2.0D, color);
        ColliderWireframe.arc(consumer, pose, center, MathUtils.ZP, MathUtils.XP, radius, 0.0D, Math.PI * 2.0D, color);
    }
}
