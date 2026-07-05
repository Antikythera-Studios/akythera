package com.yesman.akythera.client.gui.widget;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.ArmatureComponent;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.compat.playeranimlibrary.AkytheraAnimationLayer;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariants;
import net.minecraft.world.entity.animal.pig.PigVariants;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class ModelPreviewer extends AbstractWidget
    implements AnchoredWidget, CloseableWidget, ResizeCompleteCallbackListener
{
    private static final int FULL_BRIGHT = (15 * 16) | ((15 * 16) << 16);

    /// Default dimensions used for skeletal meshes that don't yet have a
    /// registered [EntityDimensions] association. Player-sized.
    public static final EntityDimensions DEFAULT_SKELETAL_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F);

    private final TreeSet<PreviewComponent> components = new TreeSet<>(Comparator.comparing(PreviewComponent::drawOrder));

    @Nullable
    private RenderTarget previewTarget;

    @Nullable
    private ProjectionMatrixBuffer projectionMatrixBuffer;

    /// Returns a multiplier to get exact pixel size on the screen, designed for AkytheraLabs.
    private final Supplier<Integer> framebufferSizeMultiplier;

    public ModelPreviewer(
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchorType,
        VerticalAnchorType verticalAnchorType,
        Supplier<Integer> framebufferSizeMultiplier
    ) {
        super(0, 0, 0, 0, Component.empty());
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        this.framebufferSizeMultiplier = framebufferSizeMultiplier;
    }

    public void addComponent(PreviewComponent component) {
        components.add(component);
    }

    public void removeComponent(PreviewComponent component) {
        if (components.remove(component)) {
            component.destroy();
        }
    }

    public void removeAllComponents(Class<? extends PreviewComponent> componentType) {
        components.removeIf(c -> componentType.isAssignableFrom(c.getClass()));
    }

    public <C extends PreviewComponent> @Nullable C getComponent(Class<C> type) {
        for (PreviewComponent c : components) {
            if (type.isInstance(c)) return type.cast(c);
        }

        return null;
    }

    /// Manually resize or create the preview render target.
    ///
    /// Call this after panel divider is released — avoids GPU overhead during drag.
    @Override
    public void onResizeCompleted() {
        int guiScale = framebufferSizeMultiplier.get();
        int physW = Math.max(8, getWidth() * guiScale);
        int physH = Math.max(8, getHeight() * guiScale);

        if (previewTarget == null) {
            previewTarget = new MainTarget(physW, physH);
            projectionMatrixBuffer = new ProjectionMatrixBuffer("model_previewer");
        } else {
            previewTarget.resize(physW, physH);
        }

        for (PreviewComponent c : components) c.onResized();
    }

    /// Release GPU resources. Call from the owning screen's onClose().
    @Override
    public void destroy() {
        if (previewTarget != null) {
            previewTarget.destroyBuffers();
            previewTarget = null;
        }

        if (projectionMatrixBuffer != null) {
            projectionMatrixBuffer.close();
            projectionMatrixBuffer = null;
        }

        for (PreviewComponent c : components) c.destroy();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        CameraComponent camera = getComponent(CameraComponent.class);

        if (previewTarget != null && camera != null && projectionMatrixBuffer != null) {
            // Clear the preview target
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                previewTarget.getColorTexture(), 0,
                previewTarget.getDepthTexture(), 1.0
            );

            // Save render state
            RenderSystem.backupProjectionMatrix();
            GpuTextureView savedColorOverride = RenderSystem.outputColorTextureOverride;
            GpuTextureView savedDepthOverride = RenderSystem.outputDepthTextureOverride;

            // Redirect all RenderType draws into previewTarget
            RenderSystem.outputColorTextureOverride = previewTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = previewTarget.getDepthTextureView();

            // Set projection matrix
            RenderSystem.setProjectionMatrix(
                projectionMatrixBuffer.getBuffer(camera.buildProjection(previewTarget.width, previewTarget.height)),
                ProjectionType.PERSPECTIVE
            );

            // Set camera model-view transform
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            PoseStack cameraPS = camera.buildPoseStack(a);
            modelViewStack.set(cameraPS.last().pose());

            // Per-frame shared render scratch — threaded through PreviewComponent#render.
            PoseStack poseStack = new PoseStack();

            try (ByteBufferBuilder byteBuf = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
                for (PreviewComponent c : components) {
                    c.extractComponent(graphics, this, poseStack, byteBuf, mouseX, mouseY, a);
                }
            }

            // Restore render state
            modelViewStack.popMatrix();

            RenderSystem.outputColorTextureOverride = savedColorOverride;
            RenderSystem.outputDepthTextureOverride = savedDepthOverride;
            RenderSystem.restoreProjectionMatrix();

            // Blit previewTarget to the widget area (v-flipped: GL render targets have bottom-up Y)
            GpuSampler sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
            );

            graphics.innerBlit(
                RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                previewTarget.getColorTextureView(),
                sampler,
                x, y, x + w, y + h,
                0.0F, 1.0F, 1.0F, 0.0F,
                0xFFFFFFFF
            );
        }

        // Border drawn on top of blit
        graphics.outline(x, y, w, h, 0xFF333333);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        CameraComponent camera = getComponent(CameraComponent.class);

        if (camera == null) return false;

        camera.handleMouseDrag(dx, dy, event.button());

        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double xScroll, double yScroll) {
        CameraComponent camera = getComponent(CameraComponent.class);

        if (camera == null || !isMouseOver(x, y)) return false;

        camera.handleMouseScroll(yScroll);

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {
    }

    // -------------- Utility functions for add or replace components ---------------

    /// Eases the camera to frame a model of the given entity dimensions.
    private static void frameCamera(@Nullable CameraComponent camera, EntityDimensions dim) {
        if (camera == null) return;

        float half = Math.max(dim.width() * 0.5F, dim.height() * 0.5F) * 1.15F;
        double distance = -Math.max(1.5F, half / (float) Math.tan(Math.toRadians(35.0))) * 2.5D;
        float yMove = dim.height() * 0.1F;

        camera.moveTo(distance, 15.0F, 180.0F, 0.0F, yMove * yMove);
    }

    @SuppressWarnings("unchecked")
    public void displayEntity(
        @Nullable Identifier entityTypeId,
        EntityModelComponent.@Nullable ModelType modelType,
        boolean deletePrevious,
        boolean locateCameraToFront
    ) {
        if (deletePrevious) removeAllComponents(EntityModelComponent.class);
        if (entityTypeId == null || modelType == null) return;

        switch (modelType) {
            case SKELETAL_MESH -> {
                AssetAccessor<? extends Mesh<?>> accessor = MeshManager.getInstance().getRegisteredAccessorById(entityTypeId);
                if (accessor == null || !accessor.checkType(AssetAccessor.Type.SKELETAL_MESH)) return;
                AssetAccessor<SkeletalMesh> skeletalMeshAccessor = (AssetAccessor<SkeletalMesh>)accessor;

                // TODO: Complete skeletal mesh renderer initialization (texture)
                EntityModelComponent c = new EntityModelComponent(skeletalMeshAccessor);
                addComponent(c);

                if (locateCameraToFront) frameCamera(getComponent(CameraComponent.class), DEFAULT_SKELETAL_DIMENSIONS);
            }
            case VANILLA_ENTITY -> {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) return;
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityTypeId);
                EntityRenderState entityRenderState = entityType == EntityType.PLAYER
                    ? new AvatarRenderState()
                    : new EntityRenderState();

                entityRenderState.entityType = entityType;
                EntityRenderer<?, ?> entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entityRenderState);

                if (!(entityRenderer instanceof LivingEntityRenderer<?, ?, ?> livingEntityRenderer)) return;

                EntityModelComponent component = new EntityModelComponent(entityType, livingEntityRenderer);
                addComponent(component);

                if (locateCameraToFront) frameCamera(getComponent(CameraComponent.class), entityType.getDimensions());
            }
        }
    }

    // ------------------------- Component implementations  -------------------------

    public interface PreviewComponent {
        void extractComponent(
            GuiGraphicsExtractor graphics,
            ModelPreviewer modelPreviewer,
            PoseStack poseStack,
            ByteBufferBuilder byteBuffer,
            int mouseX,
            int mouseY,
            float a
        );

        int drawOrder();

        default void onResized() {
        }

        default void destroy() {
        }
    }

    public record BackgroundComponent(int color) implements PreviewComponent {
        public BackgroundComponent(int r, int g, int b, int a) {
            this(ARGB.color(a, r, g, b));
        }

        @Override
        public void extractComponent(GuiGraphicsExtractor graphics, ModelPreviewer modelPreviewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
            int x = modelPreviewer.getX(), y = modelPreviewer.getY(), w = modelPreviewer.getWidth(), h = modelPreviewer.getHeight();
            graphics.fill(x, y, x + w, y + h, color);
        }

        @Override
        public int drawOrder() {
            return 0;
        }
    }

    public record GridFloorComponent(float squareLength, int cells, int color)
        implements PreviewComponent
    {
        /// Half-width (relative to one cell) of each line's alpha-fade edge.
        /// Wider = smoother but blurrier.
        private static final float HALF_WIDTH_RATIO = 0.04F;

        public GridFloorComponent(float squareLength, int cells, int r, int g, int b, int a) {
            this(squareLength, cells, ARGB.color(a, r, g, b));
        }

        @Override
        public void extractComponent(GuiGraphicsExtractor graphics, ModelPreviewer modelPreviewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
            // QUADS + POSITION_COLOR + translucent blend with no culling — lets us emit alpha-faded
            // quads instead of 1-pixel hardware lines. The fade gives Gouraud-interpolated AA edges
            // without needing an MSAA render target.
            RenderType renderType = RenderTypes.debugQuads();
            BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());

            float half = cells * squareLength * 0.5F;
            float w = squareLength * HALF_WIDTH_RATIO;

            BackgroundComponent bgComp = modelPreviewer.getComponent(BackgroundComponent.class);
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
                renderType.draw(meshData);
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
            builder.addVertex(pose, ax,      0.0F, az     ).setColor(color);
            builder.addVertex(pose, ax + dx, 0.0F, az + dz).setColor(edgeColor);
            builder.addVertex(pose, bx + dx, 0.0F, bz + dz).setColor(edgeColor);
            builder.addVertex(pose, bx,      0.0F, bz     ).setColor(color);

            // Lower half: transparent edge → solid center
            builder.addVertex(pose, ax - dx, 0.0F, az - dz).setColor(edgeColor);
            builder.addVertex(pose, ax,      0.0F, az     ).setColor(color);
            builder.addVertex(pose, bx,      0.0F, bz     ).setColor(color);
            builder.addVertex(pose, bx - dx, 0.0F, bz - dz).setColor(edgeColor);
        }

        @Override
        public int drawOrder() {
            return 1;
        }
    }

    /// Draws a root-motion trajectory: a connected path with a sampling dot every fixed step,
    /// big dots on the start/end, and a gizmo marker on the current playback position.
    ///
    /// Self-contained — it pulls positions from a [RootMotionSampler] (built by the editor screen)
    /// so it knows nothing about holders. Flat quads on the XZ plane via [RenderTypes#debugQuads]
    /// (translucent, no cull), same approach as [GridFloorComponent].
    public static final class RootMotionTrajectoryComponent implements PreviewComponent {
        private static final int LINE_COLOR  = 0xFFFFCC00; // path
        private static final int DOT_COLOR   = 0xFFFFE08A; // per-point dots
        private static final int EDGE_COLOR  = 0xFFFF4040; // big start/end dots
        private static final int GIZMO_COLOR = 0xFF44AAFF; // current playback marker
        private static final float LINE_HALF_W = 0.02F;
        private static final float DOT_HALF    = 0.04F;
        private static final float EDGE_HALF   = 0.09F;
        private static final float GIZMO_HALF  = 0.11F;

        /// Ordered polyline points of the trajectory (preview space). Built by the editor screen —
        /// a sequence/blendspace samples its root over time; a montage runs a section-ordered playback
        /// simulation. Re-fetched each frame so edits show up live.
        private final Supplier<List<Vec3>> pathSupplier;

        /// Current playback marker position (where the model's root is right now).
        private final Supplier<Vec3> gizmoSupplier;

        public RootMotionTrajectoryComponent(Supplier<List<Vec3>> pathSupplier, Supplier<Vec3> gizmoSupplier) {
            this.pathSupplier = pathSupplier;
            this.gizmoSupplier = gizmoSupplier;
        }

        @Override
        public void extractComponent(GuiGraphicsExtractor graphics, ModelPreviewer modelPreviewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
            List<Vec3> path = pathSupplier.get();
            if (path == null || path.isEmpty()) return;

            RenderType renderType = RenderTypes.debugQuads();
            BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());
            PoseStack.Pose pose = poseStack.last();

            int n = path.size();
            for (int i = 0; i < n; i++) {
                Vec3 p = path.get(i);
                if (i > 0) emitLine(builder, pose, path.get(i - 1), p, LINE_HALF_W, LINE_COLOR);

                boolean edge = (i == 0 || i == n - 1);
                emitDot(builder, pose, p, edge ? EDGE_HALF : DOT_HALF, edge ? EDGE_COLOR : DOT_COLOR);
            }

            Vec3 gizmo = gizmoSupplier.get();
            if (gizmo != null) emitDot(builder, pose, gizmo, GIZMO_HALF, GIZMO_COLOR);

            MeshData meshData = builder.build();

            if (meshData != null) {
                renderType.draw(meshData);
            }
        }

        /// Flat horizontal ribbon from a→c, widened perpendicular in the XZ plane.
        private static void emitLine(BufferBuilder builder, PoseStack.Pose pose, Vec3 a, Vec3 c, float halfW, int color) {
            double dx = c.x - a.x, dz = c.z - a.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-5) return;

            float px = (float) (-dz / len * halfW), pz = (float) (dx / len * halfW);
            builder.addVertex(pose, (float) a.x + px, (float) a.y, (float) a.z + pz).setColor(color);
            builder.addVertex(pose, (float) a.x - px, (float) a.y, (float) a.z - pz).setColor(color);
            builder.addVertex(pose, (float) c.x - px, (float) c.y, (float) c.z - pz).setColor(color);
            builder.addVertex(pose, (float) c.x + px, (float) c.y, (float) c.z + pz).setColor(color);
        }

        /// Flat diamond on the XZ plane centered at p.
        private static void emitDot(BufferBuilder builder, PoseStack.Pose pose, Vec3 p, float half, int color) {
            float x = (float) p.x, y = (float) p.y, z = (float) p.z;
            builder.addVertex(pose, x - half, y, z).setColor(color);
            builder.addVertex(pose, x, y, z - half).setColor(color);
            builder.addVertex(pose, x + half, y, z).setColor(color);
            builder.addVertex(pose, x, y, z + half).setColor(color);
        }

        @Override
        public int drawOrder() {
            return 20; // after grid (1) and model (10)
        }
    }

    /// Camera component: necessary for rendering
    public static final class CameraComponent implements PreviewComponent {
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
        public void extractComponent(GuiGraphicsExtractor graphics, ModelPreviewer modelPreviewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        }

        /// Builds the model-view PoseStack for this frame.
        /// Eases live values toward their targets using the per-frame Δticks `a`.
        /// When `a == 1` the per-frame factor `k` equals `EASE`, matching the old per-tick easing.
        public PoseStack buildPoseStack(float a) {
            float k = 1.0F - (float)Math.pow(1.0F - EASE, a);
            double kd = 1.0 - Math.pow(1.0 - EASE, a);

            zoom += (targetZoom  - zoom)  * kd;
            xRot += (targetXRot  - xRot)  * k;
            yRot += (targetYRot  - yRot)  * k;
            xMove += (targetXMove - xMove) * k;
            yMove += (targetYMove - yMove) * k;

            PoseStack ps = new PoseStack();
            ps.translate(xMove, yMove - 1.0F, (float)zoom);
            ps.mulPose(Axis.XP.rotationDegrees(xRot));
            ps.mulPose(Axis.YP.rotationDegrees(yRot));

            return ps;
        }

        /// Builds a perspective projection matrix for the given viewport size.
        public Matrix4f buildProjection(int viewW, int viewH) {
            return new Matrix4f().setPerspective(
                (float) Math.toRadians(70.0),
                (float) viewW / (float) Math.max(1, viewH),
                0.05F, 100.0F
            );
        }

        public void handleMouseDrag(double dx, double dy, int button) {
            if (!controllable) return;
            if (button == 0) {
                xRot = Mth.clamp(xRot + (float)(dy * 2.5), -90.0F, 90.0F);
                yRot += (float)(dx * 2.5F);
                targetXRot = xRot;
                targetYRot = yRot;
            } else if (button == 2) {
                xMove += (float)( dx * 0.015F * -zoom);
                yMove += (float)(-dy * 0.015F * -zoom);
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

    public static final class EntityModelComponent implements PreviewComponent, AnimatableObject {
        private class PreviewModelArmatureComponent extends ArmatureComponent {
            public PreviewModelArmatureComponent() {
                super(null, null, null, false);
            }

            @Override
            public Animator getAnimator() {
                return animator;
            }

            @Override
            public AssetAccessor<? extends Armature> getArmature() {
                return armature;
            }
        }

        /// Used when display model with skeletal mesh and armature
        @Nullable
        private AssetAccessor<? extends Armature> armature;

        /// Used when display model with skeletal mesh and armature
        @Nullable
        private AssetAccessor<? extends SkeletalMesh> mesh;

        @Nullable
        private Animator animator;

        @Nullable
        private final Identifier texture;

        /// Used when display model by PAL. Doesn't require [#armature] to resolve animated transform
        @Nullable
        private VanillaRenderInfo<?> vanillaRenderInfo;

        private final ArmatureComponent armatureComponent = new PreviewModelArmatureComponent();

        /// Mesh-only render for a skeletal mesh
        public EntityModelComponent(final @NonNull AssetAccessor<? extends SkeletalMesh> mesh) {
            this.mesh = mesh;
            this.texture = null;
        }

        /// Animated mesh render for a skeletal mesh
        @NullMarked
        public EntityModelComponent(final AssetAccessor<? extends SkeletalMesh> mesh, final AssetAccessor<? extends Armature> armature) {
            this(mesh);

            this.armature = armature;
        }

        /// Mesh-only render for a vanilla model
        ///
        /// @param entityType should be provided for cases that two entity types are sharing one renderer (e.g. Piglin and Piglin brute)
        @NullMarked @SuppressWarnings("unchecked")
        public <S extends LivingEntityRenderState, M extends EntityModel<S>> EntityModelComponent(EntityType<?> entityType, final LivingEntityRenderer<?, ?, ?> renderer) {
            animator = new SimpleHeadlessAnimator();

            LivingEntityRenderer<?, S, M> rendererCasted = (LivingEntityRenderer<?, S, M>)renderer;
            M entityModel = rendererCasted.getModel();
            S defaultRenderState = setupDefaultRenderState(entityType, rendererCasted.createRenderState());
            entityModel.setupAnim(defaultRenderState);

            this.vanillaRenderInfo = new VanillaRenderInfo<>(entityModel, defaultRenderState);
            this.texture = rendererCasted.getTextureLocation(defaultRenderState);
        }

        /// Animated model render for a vanilla model
        ///
        /// @param entityType should be provided for cases that two entity types are sharing one renderer (e.g. Piglin and Piglin brute)
        @NullMarked
        public EntityModelComponent(EntityType<?> entityType, final LivingEntityRenderer<?, ?, ?> renderer, final AssetAccessor<? extends Armature> armature) {
            this(entityType, renderer);

            this.armature = armature;
        }

        /// Setup animation for [SimpleHeadlessAnimator]
        public void setSimplePlayingAnimation(final @NonNull AssetAccessor<AnimationClip> animClip) {
            if (animator == null) {
                animator = new SimpleHeadlessAnimator();
            }

            // TODO: Decide whether only allow calling this method for SimpleHeadlessAnimator, or add accessor accepting method to play it once
            if (animator instanceof SimpleHeadlessAnimator simpleHeadlessAnimator) {
                simpleHeadlessAnimator.setAnimation(animClip);
            }
        }

        /// Setup animation for [SimpleHeadlessAnimator] and choose whether the animator should
        /// advance [SimpleHeadlessAnimator#elapsedTime] by itself (`updatePose = true`) or be driven
        /// from the outside via [SimpleHeadlessAnimator#setElapsedTime] (`updatePose = false`).
        public void setSimplePlayingAnimation(final @NonNull AssetAccessor<AnimationClip> animClip, boolean updatePose) {
            setSimplePlayingAnimation(animClip);

            if (animator instanceof SimpleHeadlessAnimator simpleHeadlessAnimator) {
                simpleHeadlessAnimator.setUpdatePose(updatePose);
            }
        }

        public void setArmature(final @NonNull AssetAccessor<? extends Armature> armature) {
            this.armature = armature;
        }

        @Override
        public void extractComponent(GuiGraphicsExtractor graphics, ModelPreviewer modelPreviewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
            if (animator != null) {
                animator.update(a * 0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            }

            if (mesh != null && mesh.get() != null && texture != null) {
                renderSkeletal(poseStack, byteBuffer);
                return;
            }

            if (vanillaRenderInfo != null && texture != null) {
                renderVanilla(poseStack, byteBuffer);
            }
        }

        private void renderSkeletal(PoseStack poseStack, ByteBufferBuilder byteBuffer) {
            SkeletalMesh skMesh = mesh.get();
            if (skMesh == null) return;

            Armature arm = armature != null ? armature.get() : null;
            if (arm == null) return; // armature is required even for bind pose (bone count)

            Matrix4f[] skinMatrices;

            if (animator != null) {
                Pose pose = animator.getPose();
                skinMatrices = pose.getForwardBoundTransforms(armature);
                arm.boundBackwardTransform(skinMatrices);
            } else {
                skinMatrices = new Matrix4f[arm.getBoneCount()];
                for (int i = 0; i < skinMatrices.length; i++) skinMatrices[i] = new Matrix4f();
            }

            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);

            skMesh.drawPosed(
                poseStack, builder,
                Mesh.DrawingFunction.NEW_ENTITY,
                FULL_BRIGHT,
                1.0F, 1.0F, 1.0F, 1.0F,
                OverlayTexture.NO_OVERLAY,
                skinMatrices
            );

            MeshData meshData = builder.build();

            if (meshData != null) {
                RenderTypes.entityTranslucent(texture).draw(meshData);
            }
        }

        /// Deprecated: shouldn't be called when PAL uninstalled
        private void renderVanilla(PoseStack poseStack, ByteBufferBuilder byteBuffer) {
            assert vanillaRenderInfo != null && texture != null;

            poseStack.pushPose();

            // Vanilla living-entity renderers apply this Y-offset in LivingEntityRenderer#submit.
            // Duplicate it so the feet sit on y=0 like the skeletal-mesh path.
            poseStack.translate(0.0F, -EntityModel.MODEL_Y_OFFSET, 0.0F);
            poseStack.scale(-1.0F, -1.0F, 1.0F); // MC-space → mesh-space

            if (armature != null && animator != null) {
                Armature arm = armature.get();

                if (arm != null) {
                    vanillaRenderInfo.updatePose(animator.getPose());
                }
            } else {
                vanillaRenderInfo.entityModel.resetPose();
            }

            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.ENTITY);

            vanillaRenderInfo.entityModel.renderToBuffer(poseStack, builder, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

            MeshData meshData = builder.build();

            if (meshData != null) {
                RenderTypes.entityTranslucent(texture).draw(meshData);
            }

            poseStack.popPose();
        }

        @Override
        public int drawOrder() {
            return 10;
        }

        /// Sets up a default texture or mob variant to avoid missing-no texture appeared in preview screen.
        ///
        /// Manual fix, should be removed when Minecraft provides universalized solution for this
        private <S extends LivingEntityRenderState> S setupDefaultRenderState(EntityType<?> entityType, S renderState) {
            if (renderState instanceof ChickenRenderState chickenRenderState) {
                chickenRenderState.variant = StaticRegistryAccess.getRegistryAccess().get(ChickenVariants.DEFAULT).orElseThrow().value();
            } else if (renderState instanceof PigRenderState pigRenderState) {
                pigRenderState.variant = StaticRegistryAccess.getRegistryAccess().get(PigVariants.DEFAULT).orElseThrow().value();
            } else if (renderState instanceof CowRenderState cowRenderState) {
                cowRenderState.variant = StaticRegistryAccess.getRegistryAccess().get(CowVariants.DEFAULT).orElseThrow().value();
            } else if (renderState instanceof PiglinRenderState piglinRenderState) {
                piglinRenderState.isBrute = entityType == EntityType.PIGLIN_BRUTE;
            } else if (renderState instanceof NautilusRenderState nautilusRenderState) {
                nautilusRenderState.variant = StaticRegistryAccess.getRegistryAccess().get(ZombieNautilusVariants.DEFAULT).orElseThrow().value();
            }

            if (renderState instanceof LivingEntityRenderStateAccessor livingEntityRenderStateAccessor) {
                // Pre-init armature here even tho not provided as a constructor parameter
                armature = ComponentsFactory.getImpl().getExternalArmatureMappingFor(entityType);

                if (armature != null) {
                    livingEntityRenderStateAccessor.akythera$setPose(Pose.EMPTY_POSE);
                    livingEntityRenderStateAccessor.akythera$setArmature(armature);
                }
            }

            if (renderState instanceof IAvatarAnimationState avatarAnimationState && armature != null) {
                // Add a headless anim manager, this would cause crash under the premise of animation-only usage
                AvatarAnimManager avatarAnimManager = new AvatarAnimManager(null);
                AkytheraAnimationLayer locomotionLayer = new AkytheraAnimationLayer(this);
                locomotionLayer.updatePose();
                avatarAnimManager.addAnimLayer(0, locomotionLayer);
                avatarAnimationState.playerAnimLib$setAnimManager(avatarAnimManager);
            }

            return renderState;
        }

        public @Nullable AssetAccessor<? extends Armature> getArmature() {
            return armature;
        }

        public @Nullable Animator getAnimator() {
            return animator;
        }

        /// Install a screen-owned animator (e.g. the editor screens' montage / blendspace animators).
        /// Pass `null` to clear. The render loop calls `animator.update(...)` + `animator.getPose()`.
        public void setAnimator(@Nullable Animator animator) {
            this.animator = animator;
        }

        @Override
        public ArmatureComponent getArmatureComponent() {
            return armatureComponent;
        }

        /// Headless previewer animators that the editor drives by hand: it sets/reads the
        /// playhead, toggles whether the animator advances itself, and gets a callback when
        /// playback reaches the end.
        public interface EditorScrubAnimator extends Animator {
            @Nullable AssetAccessor<AnimationClip> getPreviewAnimation();
            float getElapsedTime();
            void setElapsedTime(float seconds);
            void setUpdatePose(boolean updatePose);
            void setOnPlaybackEnds(Runnable onPlaybackEnds);
        }

        /// An [Animator] implementation for [ModelPreviewer] that require no live entity.
        ///
        /// Ticks elapsed time forward each game tick and samples [AnimationClip#getPose] at that time,
        /// looping when the clip ends. [#getOwner] and [#getMontagePlayer] are not supported.
        ///
        /// Useful for previewer screens without bound entity in world, like selection modal.
        public static final class SimpleHeadlessAnimator implements EditorScrubAnimator {
            @Nullable
            private AssetAccessor<AnimationClip> animation;
            private float elapsedTime = 0.0F;

            /// Whether pose update by render tick, or manual control.
            private boolean updatePose = true;

            /// Called
            @Nullable
            private Runnable onPlaybackEnds;

            public void setAnimation(@Nullable final AssetAccessor<AnimationClip> animation) {
                this.animation = animation;
                this.elapsedTime = 0.0F;
            }

            public AssetAccessor<AnimationClip> getPreviewAnimation() {
                return animation;
            }

            public void setUpdatePose(boolean updatePose) {
                this.updatePose = updatePose;
            }

            public void setOnPlaybackEnds(@Nullable Runnable onPlaybackEnds) {
                this.onPlaybackEnds = onPlaybackEnds;
            }

            public boolean isUpdatePose() {
                return updatePose;
            }

            public float getElapsedTime() {
                return elapsedTime;
            }

            public void setElapsedTime(float seconds) {
                if (animation == null) {
                    elapsedTime = Math.max(0.0F, seconds);
                    return;
                }

                AnimationClip clip = animation.getOrHandleException(Akythera.LOGGER::error);
                float total = clip == null ? Float.MAX_VALUE : clip.getTotalTime();
                elapsedTime = Math.clamp(seconds, 0.0F, total);
            }

            @Override
            public Pose getPose() {
                if (animation == null) return Pose.EMPTY_POSE;

                AnimationClip clip = animation.getOrHandleException(Akythera.LOGGER::error);
                if (clip == null) return Pose.EMPTY_POSE;

                return clip.getPose(elapsedTime);
            }

            @Override
            public void update(final float timeFrame, final float partialTick) {
                if (!updatePose || animation == null) return;

                AnimationClip clip = animation.getOrHandleException(Akythera.LOGGER::error);

                if (clip == null) return;

                float total = clip.getTotalTime();

                if (total > 0.0F) {
                    elapsedTime += timeFrame;

                    if (elapsedTime > total) {
                        if (onPlaybackEnds != null) onPlaybackEnds.run();
                        elapsedTime %= total;
                    }
                }
            }

            @Override
            public void tick() {
            }

            @Override @Deprecated
            public void setSoftPause(final boolean paused) {
            }

            @Override @Deprecated
            public void setHardPause(final boolean paused) {
            }

            @Override @Deprecated
            public boolean isSoftPaused() {
                return false;
            }

            @Override @Deprecated
            public boolean isHardPaused() {
                return false;
            }

            @Override
            public EntityPatch<Entity> getOwner() {
                throw new UnsupportedOperationException("SimpleHeadlessAnimator has no owner entity");
            }

            @Override
            public AnimationMontagePlayer getMontagePlayer(final ResourceKey<@NonNull AnimationMontage> animMontageKey) {
                throw new UnsupportedOperationException("SimpleHeadlessAnimator has no montage player");
            }
        }

        public enum ModelType implements StringRepresentable {
            SKELETAL_MESH("skeletal_mesh"),
            VANILLA_ENTITY("vanilla_entity");

            public static final Codec<ModelType> CODEC = StringRepresentable.fromEnum(ModelType::values);

            private final String serializedName;

            ModelType(final String serializedName) {
                this.serializedName = serializedName;
            }

            @Override
            public @NonNull String getSerializedName() {
                return serializedName;
            }
        }

        private record VanillaRenderInfo<S extends LivingEntityRenderState>(EntityModel<@NotNull S> entityModel, S renderState) {
            public void updatePose(Pose pose) {
                if (renderState instanceof LivingEntityRenderStateAccessor livingEntityRenderStateAccessor) {
                    livingEntityRenderStateAccessor.akythera$setPose(pose);
                }

                if (renderState instanceof IAvatarAnimationState avatarAnimationState) {
                    // No type and null checks, since `renderState` is initialized in ModelPreviewer
                    AkytheraAnimationLayer locomotionLayer = (AkytheraAnimationLayer)avatarAnimationState.playerAnimLib$getAnimManager().getLayers().getFirst().second();
                    locomotionLayer.updatePose();
                }

                entityModel.setupAnim(renderState);
            }
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        // Resize the frame buffer ONLY once when it's not initialized
        if (previewTarget == null) {
            onResizeCompleted();
        }
    }
}