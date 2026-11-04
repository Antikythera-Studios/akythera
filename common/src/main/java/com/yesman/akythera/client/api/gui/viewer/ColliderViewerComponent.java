package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.renderer.collider.ColliderRenderers;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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

        poseStack.pushPose();
        poseStack.mulPose(placementOf(shape));
        ColliderRenderers.emitWireframe(builder, poseStack.last(), shape, color);
        poseStack.popPose();

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(renderType, meshData);
        }
    }

    /// Camera distance to the nearest wireframe edge passing within `tolerancePixels` of the widget point
    /// (`mouseX`, `mouseY`), or -1 when none does.
    public float pickEdge(final Model3DViewer viewer, final double mouseX, final double mouseY, final float tolerancePixels) {
        Collider shape = collider.get();

        if (shape == null) return -1.0F;

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(placementOf(shape));

        LineRecorder lines = new LineRecorder();
        ColliderRenderers.emitWireframe(lines, poseStack.last(), shape, color);

        Vec3 cursor = new Vec3(mouseX, mouseY, 0.0D);
        Vector3f camera = viewer.getCameraPosition();
        double toleranceSqr = (double) tolerancePixels * tolerancePixels;
        float nearest = -1.0F;

        for (int i = 0; i + 1 < lines.points.size(); i += 2) {
            Vector3f from = lines.points.get(i);
            Vector3f to = lines.points.get(i + 1);
            Vector3f projectedFrom = viewer.projectToWidget(from);
            Vector3f projectedTo = viewer.projectToWidget(to);

            if (projectedFrom == null || projectedTo == null) continue;

            double distanceSqr = VectorUtils.pointToSegmentDistanceSqr(
                cursor, new Vec3(projectedFrom.x, projectedFrom.y, 0.0D), new Vec3(projectedTo.x, projectedTo.y, 0.0D)
            );

            if (distanceSqr > toleranceSqr) continue;

            float distance = camera.distance(new Vector3f(from).add(to).mul(0.5F));

            if (nearest < 0.0F || distance < nearest) nearest = distance;
        }

        return nearest;
    }

    /// The pose [ColliderRenderers#emitWireframe] draws `shape` under, in viewer world space.
    private Matrix4f placementOf(final Collider shape) {
        return ColliderRenderers.placement(shape, shape.getTransformInheritPolicy().inherit(placement.get(), shape.getPivotTransform()));
    }

    @Override
    public int drawOrder() {
        return OVERLAY_DRAW_ORDER;
    }

    /// Keeps the position of every vertex emitted into it, in the space of the pose they were emitted under. A line
    /// list fills it two points per segment.
    private static final class LineRecorder implements VertexConsumer {
        private final List<Vector3f> points = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            points.add(new Vector3f(x, y, z));
            return this;
        }

        @Override public VertexConsumer setColor(final int r, final int g, final int b, final int a) { return this; }
        @Override public VertexConsumer setColor(final int color) { return this; }
        @Override public VertexConsumer setUv(final float u, final float v) { return this; }
        @Override public VertexConsumer setUv1(final int u, final int v) { return this; }
        @Override public VertexConsumer setUv2(final int u, final int v) { return this; }
        @Override public VertexConsumer setNormal(final float x, final float y, final float z) { return this; }
        @Override public VertexConsumer setLineWidth(final float width) { return this; }
    }
}
