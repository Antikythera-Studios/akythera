package com.yesman.akythera.client.gui.widget.viewer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class Model3DViewer extends AbstractWidget
    implements AnchoredWidget, CloseableWidget, ResizeCompleteCallbackListener
{
    public static final int FULL_BRIGHT = (15 * 16) | ((15 * 16) << 16);

    private static final Vector4f TRANSPARENT_CLEAR_COLOR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    /// Default dimensions used for skeletal meshes that don't yet have a
    /// registered [EntityDimensions] association. Player-sized.
    public static final EntityDimensions DEFAULT_SKELETAL_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F);

    /// Components in draw order, kept sorted by [#addComponent]. Same-order components stay in
    /// insertion order, and duplicates of one type are allowed — a bone socket preview per socket.
    private final List<Model3DViewerComponent> components = new ArrayList<>();

    /// Adds / removes queued while [#components] is being walked, applied once the walk ends.
    private final List<Runnable> pendingStructureChanges = new ArrayList<>();

    private boolean iterating;

    @Nullable
    private Model3DViewerComponent focused;

    /// The component that took the current left-press with [Model3DViewerComponent#onMouseDown].
    /// Drags and the release go to it instead of the camera.
    @Nullable
    private Model3DViewerComponent dragTarget;

    /// Set when a press ends a gesture instead of starting one — a right-press abort, or the left
    /// press that commits a keyed transform. Swallows the rest of the gesture so the still-held left
    /// button does not fall through to the camera; cleared when that button comes up.
    private boolean dragCancelled;

    /// Widgets floating over the 3D view. Hit-tested before picking, drawn after the blit.
    private final List<AbstractWidget> overlayWidgets = new ArrayList<>();

    /// The view / projection the last extracted frame rendered with — what [#buildRay] and
    /// [#projectToWidget] work against. Rebuilding them from [CameraComponent] instead would advance
    /// the camera easing a second time, since [CameraComponent#buildPoseStack] eases as it builds.
    private final Matrix4f lastView = new Matrix4f();
    private final Matrix4f lastProjection = new Matrix4f();
    private boolean matricesValid;

    @Nullable
    private RenderTarget previewTarget;

    @Nullable
    private ProjectionMatrixBuffer projectionMatrixBuffer;

    /// Returns a multiplier to get exact pixel size on the screen, designed for AkytheraLabs.
    private final Supplier<Integer> framebufferSizeMultiplier;

    public Model3DViewer(
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

    public void addComponent(Model3DViewerComponent component) {
        if (iterating) {
            pendingStructureChanges.add(() -> addComponent(component));
            return;
        }

        // Insertion point: after the last component of the same or lower draw order. The only place
        // order can change — every drawOrder() is a constant, and a removal leaves the rest sorted.
        int index = components.size();

        while (index > 0 && components.get(index - 1).drawOrder() > component.drawOrder()) index--;

        components.add(index, component);
    }

    public void removeComponent(Model3DViewerComponent component) {
        if (iterating) {
            pendingStructureChanges.add(() -> removeComponent(component));
            return;
        }

        if (!components.remove(component)) return;

        if (focused == component) focused = null;
        if (dragTarget == component) dragTarget = null;

        component.destroy();
    }

    public void removeAllComponents(Class<? extends Model3DViewerComponent> componentType) {
        if (iterating) {
            pendingStructureChanges.add(() -> removeAllComponents(componentType));
            return;
        }

        for (Model3DViewerComponent c : List.copyOf(components)) {
            if (componentType.isInstance(c)) removeComponent(c);
        }
    }

    /// Applies whatever [#pendingStructureChanges] collected.
    private void runPendingStructureChanges() {
        while (!pendingStructureChanges.isEmpty()) {
            List<Runnable> batch = List.copyOf(pendingStructureChanges);
            pendingStructureChanges.clear();
            batch.forEach(Runnable::run);
        }
    }

    public <C extends Model3DViewerComponent> @Nullable C getComponent(Class<C> type) {
        for (Model3DViewerComponent c : components) {
            if (type.isInstance(c)) return type.cast(c);
        }

        return null;
    }

    /// Returns the `index`-th component of `type` in draw order, or null if there are fewer
    /// than `index + 1` of them.
    public <C extends Model3DViewerComponent> @Nullable C getComponent(Class<C> type, int index) {
        if (index < 0) return null;

        int matched = 0;

        for (Model3DViewerComponent c : components) {
            if (type.isInstance(c) && matched++ == index) return type.cast(c);
        }

        return null;
    }

    /// Clears the preview target's depth, so what is drawn next reads over the scene already in it.
    /// Depth clears to the far plane, which reversed-Z puts at 0.
    public void clearDepth() {
        if (previewTarget == null) return;

        GpuTexture depthTexture = previewTarget.getDepthTexture();

        if (depthTexture == null) return;

        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 0.0);
    }

    /// Adds a widget floating over the 3D view. Owned by the caller, which removes it again.
    public void addOverlayWidget(AbstractWidget widget) {
        overlayWidgets.add(widget);
        if (widget instanceof AnchoredWidget anchored) anchored.relocate(getRectangle());
    }

    public void removeOverlayWidget(AbstractWidget widget) {
        overlayWidgets.remove(widget);
    }

    public @Nullable Model3DViewerComponent getFocused() {
        return focused;
    }

    /// Switches focus, handing `next` to the outgoing component before running the incoming one's
    /// hook. Whatever overlay a focus brings with it is the components' business, not the viewer's.
    public void focus(@Nullable Model3DViewerComponent next) {
        if (focused == next) return;

        Model3DViewerComponent previous = focused;
        focused = next;

        if (previous != null) previous.onReleased(this, next);
        if (next != null) next.onFocused(this);

        runPendingStructureChanges();
    }

    /// The focusable component whose [Model3DViewerComponent#rayTrace] hit is nearest the camera.
    public @Nullable Model3DViewerComponent pick(double mouseX, double mouseY) {
        Ray ray = buildRay(mouseX, mouseY);

        if (ray == null) return null;

        Model3DViewerComponent nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        iterating = true;

        try {
            for (Model3DViewerComponent c : components) {
                if (!c.canBeFocused(this)) continue;

                float distance = c.rayTrace(this, ray);

                if (distance < 0.0F || distance >= nearestDistance) continue;

                nearest = c;
                nearestDistance = distance;
            }
        } finally {
            iterating = false;
            runPendingStructureChanges();
        }

        return nearest;
    }

    /// Unprojects a widget-space cursor position into a world-space ray. Null before the first frame
    /// has been extracted.
    public @Nullable Ray buildRay(double mouseX, double mouseY) {
        if (!matricesValid) return null;

        float ndcX = (float) ((mouseX - getX()) / getWidth()) * 2.0F - 1.0F;
        float ndcY = 1.0F - (float) ((mouseY - getY()) / getHeight()) * 2.0F;

        Matrix4f inverse = new Matrix4f(lastProjection).mul(lastView).invert();
        Vector3f near = inverse.transformProject(new Vector3f(ndcX, ndcY, -1.0F));
        Vector3f far = inverse.transformProject(new Vector3f(ndcX, ndcY, 1.0F));

        return new Ray(near, far.sub(near).normalize());
    }

    /// A world-space point in widget coordinates (`x`, `y`) plus its NDC depth (`z`). Null when the
    /// point is behind the camera or no frame has been extracted. The counterpart of [#buildRay].
    public @Nullable Vector3f projectToWidget(Vector3f world) {
        if (!matricesValid) return null;

        Vector4f clip = new Matrix4f(lastProjection).mul(lastView)
            .transform(new Vector4f(world, 1.0F));

        if (clip.w <= 1.0e-4F) return null;

        return new Vector3f(
            getX() + (clip.x / clip.w * 0.5F + 0.5F) * getWidth(),
            getY() + (0.5F - clip.y / clip.w * 0.5F) * getHeight(),
            clip.z / clip.w
        );
    }

    /// Camera right / up in world space — the rows of the view rotation, for billboard geometry.
    public Vector3f getCameraRight() {
        return new Vector3f(lastView.m00(), lastView.m10(), lastView.m20());
    }

    public Vector3f getCameraUp() {
        return new Vector3f(lastView.m01(), lastView.m11(), lastView.m21());
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

        for (Model3DViewerComponent c : components) c.onResized();
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

        for (Model3DViewerComponent c : components) c.destroy();

        overlayWidgets.clear();
        focused = null;
        dragTarget = null;
        matricesValid = false;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        CameraComponent camera = getComponent(CameraComponent.class);

        if (previewTarget != null && camera != null && projectionMatrixBuffer != null) {
            // Clear the preview target. Depth clears to the far plane, which reversed-Z puts at 0.
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                previewTarget.getColorTexture(), TRANSPARENT_CLEAR_COLOR,
                previewTarget.getDepthTexture(), 0.0
            );

            // Save render state
            RenderSystem.backupProjectionMatrix();
            GpuTextureView savedColorOverride = RenderSystem.outputColorTextureOverride;
            GpuTextureView savedDepthOverride = RenderSystem.outputDepthTextureOverride;

            // Redirect all RenderType draws into previewTarget
            RenderSystem.outputColorTextureOverride = previewTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = previewTarget.getDepthTextureView();

            Matrix4f projection = camera.buildProjection(previewTarget.width, previewTarget.height);

            // Set projection matrix
            RenderSystem.setProjectionMatrix(
                projectionMatrixBuffer.getBuffer(projection),
                ProjectionType.PERSPECTIVE
            );

            // Set camera model-view transform
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            PoseStack cameraPS = camera.buildPoseStack(a);
            modelViewStack.set(cameraPS.last().pose());

            lastView.set(cameraPS.last().pose());
            lastProjection.set(projection);
            matricesValid = true;

            // Per-frame shared render scratch — threaded through PreviewComponent#render.
            PoseStack poseStack = new PoseStack();
            iterating = true;

            try (ByteBufferBuilder byteBuf = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
                for (Model3DViewerComponent c : components) {
                    c.extractComponent(graphics, this, poseStack, byteBuf, mouseX, mouseY, a);
                }
            } finally {
                iterating = false;
            }

            runPendingStructureChanges();

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

        for (AbstractWidget widget : overlayWidgets) {
            widget.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    /// Accepts left, right and middle. The vanilla gate is button 0 only, which would swallow the
    /// context menu and the camera pan.
    @Override
    protected boolean isValidClickButton(@NonNull MouseButtonInfo buttonInfo) {
        int button = buttonInfo.button();
        return button == 0 || button == 1 || button == 2;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) return false;

        for (AbstractWidget widget : overlayWidgets) {
            if (widget.mouseClicked(event, doubleClick)) return true;
        }

        if (event.button() == 1) {
            // Right-press during a drag aborts it. The menu is not offered — the gesture in flight
            // is what the press is answering.
            if (dragTarget != null) {
                Model3DViewerComponent cancelled = dragTarget;
                dragTarget = null;
                dragCancelled = true;
                cancelled.onDragCancelled(this);
                runPendingStructureChanges();
                return true;
            }

            // No button is held for a keyed gesture, so the abort leaves dragCancelled alone: only a
            // button-0 release clears it.
            if (focused != null && focused.isCapturingMouse(this)) {
                focused.onDragCancelled(this);
                runPendingStructureChanges();
                return true;
            }

            if (focused != null) focused.showOptions(this);
            return true;
        }

        // Middle press only opens a camera pan; the drag itself arrives through mouseDragged.
        if (event.button() != 0) return true;

        // The commit lands on the press, with the button still down — dragCancelled keeps the rest of
        // it off the camera until it comes up.
        if (focused != null && focused.isCapturingMouse(this)) {
            dragCancelled = true;
            focused.onCaptureConfirmed(this);
            runPendingStructureChanges();
            return true;
        }

        // The focused component gets the press before anything is re-picked.
        if (focused != null && focused.onMouseDown(this, event)) {
            dragTarget = focused;
            runPendingStructureChanges();
            return true;
        }

        // A press that hits nothing leaves the focus alone — empty space is where the camera is
        // dragged from, and losing the gizmo on every orbit makes it unusable.
        Model3DViewerComponent picked = pick(event.x(), event.y());

        if (picked != null) focus(picked);

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (dragCancelled) return true;

        if (dragTarget != null && event.button() == 0) {
            dragTarget.onMouseDragged(this, event, dx, dy);
            runPendingStructureChanges();
            return true;
        }

        CameraComponent camera = getComponent(CameraComponent.class);

        if (camera == null) return false;

        camera.handleMouseDrag(dx, dy, event.button());

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0) dragCancelled = false;

        // Position-gated: AbstractWidget#mouseReleased answers true for any valid button wherever the
        // cursor is, which would swallow every release and leave [#dragTarget] latched.
        for (AbstractWidget widget : overlayWidgets) {
            if (widget.isMouseOver(event.x(), event.y()) && widget.mouseReleased(event)) return true;
        }

        if (dragTarget == null) return false;

        Model3DViewerComponent target = dragTarget;
        dragTarget = null;
        target.onMouseReleased(this, event);
        runPendingStructureChanges();

        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double xScroll, double yScroll) {
        CameraComponent camera = getComponent(CameraComponent.class);

        if (camera == null || !isMouseOver(x, y)) return false;

        camera.handleMouseScroll(yScroll);

        return true;
    }

    /// Routes a key press to the focused component.
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!isActive() || focused == null || !focused.onKeyPressed(this, event)) return false;

        runPendingStructureChanges();

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {
    }

    // -------------- Utility functions for add or replace components ---------------

    /// Eases the camera to frame a model of the given entity dimensions.
    private static void frameCamera(@Nullable CameraComponent camera, EntityDimensions dim) {
        frameCamera(camera, dim.width(), dim.height());
    }

    /// Eases this viewer's camera to frame a footprint of `width` × `height` blocks.
    public void frameCamera(float width, float height) {
        frameCamera(getComponent(CameraComponent.class), width, height);
    }

    private static void frameCamera(@Nullable CameraComponent camera, float width, float height) {
        if (camera == null) return;

        float half = Math.max(width * 0.5F, height * 0.5F) * 1.15F;
        double distance = -Math.max(1.5F, half / (float) Math.tan(Math.toRadians(35.0))) * 2.5D;
        float yMove = height * 0.1F;

        camera.moveTo(distance, 15.0F, 180.0F, 0.0F, yMove * yMove);
    }

    /// The mesh's authored footprint, or [#DEFAULT_SKELETAL_DIMENSIONS] when the asset cannot be read.
    private static EntityDimensions footprintOf(AssetAccessor<? extends Mesh<?>> accessor) {
        Mesh<?> mesh = accessor.getOrHandleException(
            (ResourceLoadingFailureException e) -> Akythera.LOGGER.error("Failed at reading mesh {}", accessor.identifier(), e)
        );

        if (mesh == null) return DEFAULT_SKELETAL_DIMENSIONS;

        MeshLoader.Metadata metadata = mesh.getMetadata();

        return EntityDimensions.scalable(metadata.width(), metadata.height());
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

                if (locateCameraToFront) frameCamera(getComponent(CameraComponent.class), footprintOf(skeletalMeshAccessor));
            }
            case VANILLA_ENTITY -> {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) return;
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityTypeId);
                EntityRenderer<?, ?> entityRenderer = RenderUtils.rendererOf(entityType);

                if (!(entityRenderer instanceof LivingEntityRenderer<?, ?, ?> livingEntityRenderer)) return;

                EntityModelComponent component = new EntityModelComponent(entityType, livingEntityRenderer);
                addComponent(component);

                if (locateCameraToFront) frameCamera(getComponent(CameraComponent.class), entityType.getDimensions());
            }
        }
    }

    // ***************************************************************
    // `AnchoredWidget` implementations                              *
    // ***************************************************************
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

        for (AbstractWidget widget : overlayWidgets) {
            if (widget instanceof AnchoredWidget anchored) anchored.relocate(getRectangle());
        }

        // Resize the frame buffer ONLY once when it's not initialized
        if (previewTarget == null) {
            onResizeCompleted();
        }
    }
}