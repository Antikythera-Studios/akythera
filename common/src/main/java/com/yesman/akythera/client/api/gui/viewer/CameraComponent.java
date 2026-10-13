package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Predicate;

/// Camera component: necessary for rendering
public final class CameraComponent implements Model3DViewerComponent {
    /// Per-tick easing factor. 0 = frozen, 1 = snap. Frame-rate-corrected by `a`.
    private static final float EASE = 0.25F;

    /// Vertical field of view of [ViewMode#FRUSTUM], in degrees.
    private static final float FOV = 70.0F;

    private static final float BEHIND_YAW = 0.0F; // behind a model, looking the way it faces

    private ViewMode viewMode = ViewMode.FRUSTUM;

    /// Distance from the camera to [#focus], negated.
    private double zoom = -3.0;
    private float xRot = 0.0F;
    private float yRot = 180.0F;

    /// The point the camera orbits and looks at, in world space.
    private final Vector3f focus = new Vector3f(0.0F, 1.0F, 0.0F);

    // Target values the camera is interpolating toward
    private double targetZoom = -3.0;
    private float targetXRot = 0.0F;
    private float targetYRot = 180.0F;
    private final Vector3f targetFocus = new Vector3f(0.0F, 1.0F, 0.0F);

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
        focus.lerp(targetFocus, k);

        PoseStack ps = new PoseStack();
        ps.translate(0.0F, 0.0F, (float) zoom);
        ps.mulPose(Axis.XP.rotationDegrees(xRot));
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.translate(-focus.x, -focus.y, -focus.z);

        return ps;
    }

    /// Builds the [#viewMode] projection matrix for the given viewport size. The orthographic box
    /// matches the frustum's framing at [#focus].
    ///
    /// Near and far are handed over swapped, which is what turns the matrix reversed-Z. Vanilla
    /// does the same in `Projection#getMatrix` and every depth buffer it clears goes to 0, so a
    /// preview drawn through the render types has to agree.
    public Matrix4f buildProjection(int viewW, int viewH) {
        float aspect = (float) viewW / (float) Math.max(1, viewH);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();

        return switch (viewMode) {
            case FRUSTUM -> new Matrix4f().setPerspective((float) Math.toRadians(FOV), aspect, 100.0F, 0.05F, zZeroToOne);
            case ORTHOGRAPHIC -> {
                float halfHeight = (float) -zoom * (float) Math.tan(Math.toRadians(FOV * 0.5F));
                float halfWidth = halfHeight * aspect;

                yield new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, 100.0F, -100.0F, zZeroToOne);
            }
        };
    }

    public void handleMouseDrag(double dx, double dy, int button) {
        if (!controllable) return;
        if (button == 0) {
            xRot = Mth.clamp(xRot + (float) (dy * 2.5), -90.0F, 90.0F);
            yRot += (float) (dx * 2.5F);
            targetXRot = xRot;
            targetYRot = yRot;
        } else if (button == 1 || button == 2) {
            // Rows of the view rotation: the camera's right and up in world space.
            Matrix3f rotation = new Matrix3f().rotationX(xRot * Mth.DEG_TO_RAD).rotateY(yRot * Mth.DEG_TO_RAD);
            float scale = 0.015F * (float) -zoom;

            focus.add(rotation.getRow(0, new Vector3f()).mul((float) -dx * scale));
            focus.add(rotation.getRow(1, new Vector3f()).mul((float) dy * scale));
            targetFocus.set(focus);
        }
    }

    public void handleMouseScroll(double yScroll) {
        if (!controllable) return;
        zoom = Mth.clamp(zoom + yScroll * 0.5, -20.0, -0.5);
        targetZoom = zoom;
    }

    /// Queue a new camera transform. The camera eases toward it on every tick.
    public void moveTo(double zoom, float xRot, float yRot, Vector3f focus) {
        targetZoom = zoom;
        targetXRot = xRot;
        targetYRot = yRot;
        targetFocus.set(focus);
    }

    /// Snap the camera to a transform without interpolation.
    public void setCameraTransform(double zoom, float xRot, float yRot, Vector3f focus) {
        this.zoom = targetZoom = zoom;
        this.xRot = targetXRot = xRot;
        this.yRot = targetYRot = yRot;
        this.focus.set(focus);
        this.targetFocus.set(focus);
    }

    /// Eases around [#focus] to `xRot` / `yRot`, in degrees, over the shorter turn. Keeps the zoom.
    public void orbitTo(float xRot, float yRot) {
        targetXRot = xRot;
        targetYRot += Mth.degreesDifference(targetYRot, yRot);
    }

    /// The eased yaw this frame drew with, in degrees. What a component watching the user spin the
    /// view compares between frames.
    public float getYRot() {
        return yRot;
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    @Override
    public boolean onKeyPressed(final Model3DViewer owner, final KeyEvent event) {
        if (!controllable) return false;

        if (Shortcut.TOGGLE_VIEW_MODE.matches(event)) {
            viewMode = viewMode == ViewMode.FRUSTUM ? ViewMode.ORTHOGRAPHIC : ViewMode.FRUSTUM;
            return true;
        }

        if (Shortcut.VIEW_BACK.matches(event)) {
            orbitTo(0.0F, BEHIND_YAW);
            return true;
        }

        if (Shortcut.VIEW_RIGHT.matches(event)) {
            orbitTo(0.0F, BEHIND_YAW - 90.0F);
            return true;
        }

        if (Shortcut.VIEW_TOP.matches(event)) {
            orbitTo(90.0F, BEHIND_YAW);
            return true;
        }

        if (Shortcut.VIEW_OPPOSITE.matches(event)) {
            orbitTo(-targetXRot, targetYRot + 180.0F);
            return true;
        }

        return false;
    }

    @Override
    public int drawOrder() {
        return -1;
    }

    /// How the camera projects the scene.
    public enum ViewMode {
        FRUSTUM(ProjectionType.PERSPECTIVE),
        ORTHOGRAPHIC(ProjectionType.ORTHOGRAPHIC);

        private final ProjectionType projectionType;

        ViewMode(ProjectionType projectionType) {
            this.projectionType = projectionType;
        }

        public ProjectionType projectionType() {
            return projectionType;
        }
    }

    /// Camera keys, answered when the focused component leaves the press. Blender's numpad by default.
    public enum Shortcut {
        TOGGLE_VIEW_MODE(InputConstants.KEY_NUMPAD5),
        VIEW_BACK(InputConstants.KEY_NUMPAD1),
        VIEW_RIGHT(InputConstants.KEY_NUMPAD3),
        VIEW_TOP(InputConstants.KEY_NUMPAD7),
        VIEW_OPPOSITE(InputConstants.KEY_NUMPAD9);

        private Predicate<KeyEvent> matcher;

        Shortcut(int defaultKey) {
            this.matcher = event -> event.key() == defaultKey;
        }

        /// Replaces the key test.
        public void bind(Predicate<KeyEvent> matcher) {
            this.matcher = matcher;
        }

        public boolean matches(KeyEvent event) {
            return matcher.test(event);
        }
    }
}
