package com.yesman.akythera.client.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;

public record GridFloorComponent(float squareLength, int cells, int color)
    implements Model3DViewerComponent {
    /// Half-width (relative to one cell) of each line's alpha-fade edge.
    /// Wider = smoother but blurrier.
    private static final float HALF_WIDTH_RATIO = 0.04F;

    public GridFloorComponent(float squareLength, int cells, int r, int g, int b, int a) {
        this(squareLength, cells, ARGB.color(a, r, g, b));
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        // QUADS + POSITION_COLOR + translucent blend with no culling — lets us emit alpha-faded
        // quads instead of 1-pixel hardware lines. The fade gives Gouraud-interpolated AA edges
        // without needing an MSAA render target.
        RenderType renderType = RenderTypes.debugQuads();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        float half = cells * squareLength * 0.5F;
        float w = squareLength * HALF_WIDTH_RATIO;

        BackgroundComponent bgComp = model3DViewer.getComponent(BackgroundComponent.class);
        int edgeColor = bgComp != null ? bgComp.color() : color & 0x00FFFFFF;
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i <= cells; i++) {
            float offset = -half + i * squareLength;

            // Line along X axis at z = offset (perpendicular = +Z)
            emitFadedLineQuads(builder, pose, -half, offset, half, offset, 0.0F, w, edgeColor);
            // Line along Z axis at x = offset (perpendicular = +X)
            emitFadedLineQuads(builder, pose, offset, -half, offset, half, w, 0.0F, edgeColor);
        }

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(renderType, meshData);
        }
    }

    /// Emits two QUADS forming a thin alpha-faded strip from `(ax,0,az)` to `(bx,0,bz)`.
    /// The center seam is solid; both perpendicular edges fade to alpha=0 for soft edges.
    private void emitFadedLineQuads(
        BufferBuilder builder, PoseStack.Pose pose,
        float ax, float az, float bx, float bz,
        float dx, float dz, int edgeColor
    ) {
        // Upper half: solid center → transparent edge
        builder.addVertex(pose, ax, 0.0F, az).setColor(color);
        builder.addVertex(pose, ax + dx, 0.0F, az + dz).setColor(edgeColor);
        builder.addVertex(pose, bx + dx, 0.0F, bz + dz).setColor(edgeColor);
        builder.addVertex(pose, bx, 0.0F, bz).setColor(color);

        // Lower half: transparent edge → solid center
        builder.addVertex(pose, ax - dx, 0.0F, az - dz).setColor(edgeColor);
        builder.addVertex(pose, ax, 0.0F, az).setColor(color);
        builder.addVertex(pose, bx, 0.0F, bz).setColor(color);
        builder.addVertex(pose, bx - dx, 0.0F, bz - dz).setColor(edgeColor);
    }

    @Override
    public int drawOrder() {
        return 1;
    }
}
