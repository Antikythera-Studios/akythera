package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.renderer.collider.ColliderRenderers;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// Draws one [Collider] in the viewer, its pivot placed by [Collider#getTransformInheritPolicy] in a placement the
/// owner supplies—a bone socket's bound transform for a collider hanging off an armature, the identity for one shown
/// on its own.
///
/// Both suppliers are read every frame, so the shape and the placement may change under it.
public final class ColliderViewerComponent implements Model3DViewerComponent {
    private final Supplier<@Nullable Collider> collider;
    private final Supplier<Transform> placement;

    private int color = 0xFF33FF66;

    public ColliderViewerComponent(final Supplier<@Nullable Collider> collider, final Supplier<Transform> placement) {
        this.collider = collider;
        this.placement = placement;
    }

    public void setColor(final int color) {
        this.color = color;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics,
        Model3DViewer model3DViewer,
        PoseStack poseStack,
        ByteBufferBuilder byteBuffer,
        int mouseX,
        int mouseY,
        float a
    ) {
        Collider shape = collider.get();

        if (shape == null) return;

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        Transform transform = shape.getTransformInheritPolicy().inherit(placement.get(), shape.getPivotTransform());

        poseStack.pushPose();
        poseStack.mulPose(ColliderRenderers.placement(shape, transform));
        ColliderRenderers.emitWireframe(builder, poseStack.last(), shape, color);
        poseStack.popPose();

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(renderType, meshData);
        }
    }

    @Override
    public int drawOrder() {
        return OVERLAY_DRAW_ORDER;
    }
}
