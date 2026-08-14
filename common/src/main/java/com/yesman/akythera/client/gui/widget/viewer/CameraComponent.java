package com.yesman.akythera.client.gui.widget.viewer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/// Camera component: necessary for rendering
public final class CameraComponent implements Model3DViewerComponent {
    /// Per-tick easing factor. 0 = frozen, 1 = snap. Frame-rate-corrected by `a`.
    private static final float EASE = 0.25F;

    private double zoom = -3.0;
    private float xRot = 0.0F;
    private float yRot = 180.0F;
    private float xMove = 0.0F;
    private float yMove = 0.0F;

    // Target values the camera is interpolating toward
    private double targetZoom = -3.0;
    private float targetXRot = 0.0F;
    private float targetYRot = 180.0F;
    private float targetXMove = 0.0F;
    private float targetYMove = 0.0F;

    private final boolean controllable;

    public CameraComponent(boolean controllable) {
        this.controllable = controllable;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
    }

    /// Builds the model-view PoseStack for this frame.
    /// Eases live values toward their targets using the per-frame Δticks `a`.
    /// When `a == 1` the per-frame factor `k` equals `EASE`, matching the old per-tick easing.
    public PoseStack buildPoseStack(float a) {
        float k = 1.0F - (float) Math.pow(1.0F - EASE, a);
        double kd = 1.0 - Math.pow(1.0 - EASE, a);

        zoom += (targetZoom - zoom) * kd;
        xRot += (targetXRot - xRot) * k;
        yRot += (targetYRot - yRot) * k;
        xMove += (targetXMove - xMove) * k;
        yMove += (targetYMove - yMove) * k;

        PoseStack ps = new PoseStack();
        ps.translate(xMove, yMove - 1.0F, (float) zoom);
        ps.mulPose(Axis.XP.rotationDegrees(xRot));
        ps.mulPose(Axis.YP.rotationDegrees(yRot));

        return ps;
    }

    /// Builds a perspective projection matrix for the given viewport size.
    ///
    /// Near and far are handed over swapped, which is what turns the matrix reversed-Z. Vanilla
    /// does the same in `Projection#getMatrix` and every depth buffer it clears goes to 0, so a
    /// preview drawn through the render types has to agree.
    public Matrix4f buildProjection(int viewW, int viewH) {
        return new Matrix4f().setPerspective(
            (float) Math.toRadians(70.0),
            (float) viewW / (float) Math.max(1, viewH),
            100.0F, 0.05F,
            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
        );
    }

    public void handleMouseDrag(double dx, double dy, int button) {
        if (!controllable) return;
        if (button == 0) {
            xRot = Mth.clamp(xRot + (float) (dy * 2.5), -90.0F, 90.0F);
            yRot += (float) (dx * 2.5F);
            targetXRot = xRot;
            targetYRot = yRot;
        } else if (button == 2) {
            xMove += (float) (dx * 0.015F * -zoom);
            yMove += (float) (-dy * 0.015F * -zoom);
            targetXMove = xMove;
            targetYMove = yMove;
        }
    }

    public void handleMouseScroll(double yScroll) {
        if (!controllable) return;
        zoom = Mth.clamp(zoom + yScroll * 0.5, -20.0, -0.5);
        targetZoom = zoom;
    }

    /// Queue a new camera transform. The camera eases toward it on every tick.
    public void moveTo(double zoom, float xRot, float yRot, float xMove, float yMove) {
        targetZoom = zoom;
        targetXRot = xRot;
        targetYRot = yRot;
        targetXMove = xMove;
        targetYMove = yMove;
    }

    /// Snap the camera to a transform without interpolation.
    public void setCameraTransform(double zoom, float xRot, float yRot, float xMove, float yMove) {
        this.zoom = targetZoom = zoom;
        this.xRot = targetXRot = xRot;
        this.yRot = targetYRot = yRot;
        this.xMove = targetXMove = xMove;
        this.yMove = targetYMove = yMove;
    }

    @Override
    public int drawOrder() {
        return -1;
    }
}
