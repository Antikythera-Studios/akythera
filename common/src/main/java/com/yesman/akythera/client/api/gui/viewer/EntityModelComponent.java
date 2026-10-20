package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import com.mojang.serialization.Codec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.compat.playeranimlibrary.AkytheraAnimationLayer;
import com.yesman.akythera.client.compat.playeranimlibrary.PlayerAnimationLibraryPlugin;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariants;
import net.minecraft.world.entity.animal.pig.PigVariants;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/// The previewed model, and the owner of the [ArmatureComponent] that poses it.
public final class EntityModelComponent implements Model3DViewerComponent, AnimatableObject, ComponentAttachable {
    /// Vertex tint of every previewed mesh, ARGB.
    private static final int NO_TINT = 0xFFFFFFFF;

    /// The name [#armatureComponent] is attached under, and the name of the [PALPoseBinder] under it.
    private static final String ARMATURE = "armature";
    private static final String PAL_POSE_BINDER = "pal_pose_binder";

    /// Used when display model with skeletal mesh and armature
    @Nullable
    private AssetAccessor<? extends Armature> armature;

    /// Used when display model with skeletal mesh and armature
    @Nullable
    private AssetAccessor<? extends SkeletalMesh> mesh;

    @Nullable
    private Animator animator;

    /// A pose supplied from outside instead of read off [#animator]. Installed by a screen that
    /// already advances one, so a second model can share a body's pose without advancing it twice.
    private @Nullable Supplier<@Nullable Pose> poseSource;

    /// Whether the model is drawn. A hidden model still advances its animator, so whatever reads its
    /// pose keeps moving.
    private boolean modelVisible = true;

    /// Bound by the vanilla constructors from the entity renderer. A skeletal mesh takes its texture
    /// from [#previewTexture] instead.
    @Nullable
    private final Identifier texture;

    /// The texture a skeletal draw binds, read per frame so an editor field naming it reaches the
    /// preview while it is typed. Defaults to the one the mesh's own sidecar names, and null falls
    /// back to [MissingTextureAtlasSprite#getLocation].
    private Supplier<@Nullable Identifier> previewTexture = () -> null;

    /// Used when display model by PAL. Doesn't require [#armature] to resolve animated transform
    @Nullable
    private VanillaRenderInfo vanillaRenderInfo;

    /// Built over [#armature] and [#animator], and rebuilt whenever either changes. Null only while the first
    /// one is being built.
    private ArmatureComponent armatureComponent;

    /// The armature's transform, read every frame into [#armatureComponent]. Null leaves the transform as last set.
    private @Nullable Supplier<Transform> transformSource;

    /// Scratch the bound skeleton is resolved into for the [SkeletalMesh#drawPosed] fallback.
    private Matrix4f[] boundTransforms = Matrix4fUtils.allocateMatrixArray(0);

    /// Mesh-only render for a skeletal mesh
    public EntityModelComponent(final @NonNull AssetAccessor<? extends SkeletalMesh> mesh) {
        this.mesh = mesh;
        this.texture = null;
        this.previewTexture = () -> sidecarTexture(mesh);

        rebuildArmatureComponent();
    }

    /// The texture the mesh's own [MeshLoader.Metadata] names, or null when it names none.
    private static @Nullable Identifier sidecarTexture(final AssetAccessor<? extends SkeletalMesh> mesh) {
        SkeletalMesh resolved = mesh.getOrHandleException(Akythera.LOGGER::error);
        return resolved == null ? null : resolved.getMetadata().texture();
    }

    /// Overrides the previewed texture with a live source — the mesh asset editor's own field, so the
    /// preview follows it as it is typed instead of only after a save.
    public void setPreviewTexture(final Supplier<@Nullable Identifier> previewTexture) {
        this.previewTexture = previewTexture;
    }

    /// Animated mesh render for a skeletal mesh
    @NullMarked
    public EntityModelComponent(final AssetAccessor<? extends SkeletalMesh> mesh, final AssetAccessor<? extends Armature> armature) {
        this(mesh);

        this.armature = armature;
        rebuildArmatureComponent();
    }

    /// Mesh-only render for a vanilla model
    ///
    /// @param entityType should be provided for cases that two entity types are sharing one renderer (e.g. Piglin and Piglin brute)
    @NullMarked
    @SuppressWarnings("unchecked")
    public <S extends LivingEntityRenderState, M extends EntityModel<S>> EntityModelComponent(EntityType<?> entityType, final LivingEntityRenderer<?, ?, ?> renderer) {
        animator = new SimpleHeadlessAnimator();

        LivingEntityRenderer<?, S, M> rendererCasted = (LivingEntityRenderer<?, S, M>) renderer;
        M entityModel = rendererCasted.getModel();
        S defaultRenderState = setupDefaultRenderState(entityType, rendererCasted.createRenderState());
        entityModel.setupAnim(defaultRenderState);

        this.vanillaRenderInfo = new VanillaRenderInfo((EntityModel<LivingEntityRenderState>)entityModel, defaultRenderState);
        this.texture = rendererCasted.getTextureLocation(defaultRenderState);

        rebuildArmatureComponent();
    }

    /// Animated model render for a vanilla model
    ///
    /// @param entityType should be provided for cases that two entity types are sharing one renderer (e.g. Piglin and Piglin brute)
    @NullMarked
    public EntityModelComponent(EntityType<?> entityType, final LivingEntityRenderer<?, ?, ?> renderer, final AssetAccessor<? extends Armature> armature) {
        this(entityType, renderer);

        this.armature = armature;
        rebuildArmatureComponent();
    }

    /// Setup animation for [SimpleHeadlessAnimator]. `null` clears the clip while keeping the
    /// animator, so the model falls back to the bind pose ([SimpleHeadlessAnimator#getPose]) instead
    /// of freezing on the last one it was posed into.
    public void setSimplePlayingAnimation(final @Nullable AssetAccessor<AnimationClip> animClip) {
        if (animator == null) {
            animator = new SimpleHeadlessAnimator();
            rebuildArmatureComponent();
        }

        // TODO: Decide whether only allow calling this method for SimpleHeadlessAnimator, or add accessor accepting method to play it once
        if (animator instanceof SimpleHeadlessAnimator simpleHeadlessAnimator) {
            simpleHeadlessAnimator.setAnimation(animClip);
        }
    }

    /// Setup animation for [SimpleHeadlessAnimator] and choose whether the animator should
    /// advance [SimpleHeadlessAnimator#elapsedTime] by itself (`updatePose = true`) or be driven
    /// from the outside via [SimpleHeadlessAnimator#setElapsedTime] (`updatePose = false`).
    public void setSimplePlayingAnimation(final @Nullable AssetAccessor<AnimationClip> animClip, boolean updatePose) {
        setSimplePlayingAnimation(animClip);

        if (animator instanceof SimpleHeadlessAnimator simpleHeadlessAnimator) {
            simpleHeadlessAnimator.setUpdatePose(updatePose);
        }
    }

    public void setArmature(final @NonNull AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
        rebuildArmatureComponent();
    }

    /// Builds [#armatureComponent] over the current [#armature] and [#animator], keeping the last one's transform. A
    /// vanilla model gets a [PALPoseBinder] under it, through which [AkytheraAnimationLayer] poses the model.
    private void rebuildArmatureComponent() {
        Map<String, ComponentBlueprint<?, ?>> children = vanillaRenderInfo != null
            ? Map.of(PAL_POSE_BINDER, new PALPoseBinder())
            : Map.of();
        Transform transform = armatureComponent == null ? Transform.IDENTITY : armatureComponent.localTransform();

        armatureComponent = new ArmatureComponent(this, armature, animator, transform, TransformInheritPolicy.LOC_ROT_SCA, children);
    }

    /// The pose to skin by, read per frame in place of this component's own animator. A component with
    /// one advances nothing.
    public void setPoseSource(final @Nullable Supplier<@Nullable Pose> poseSource) {
        this.poseSource = poseSource;
    }

    /// The armature's transform, read per frame, which places the drawn model and everything posed off it.
    public void setTransformSource(final @Nullable Supplier<Transform> transformSource) {
        this.transformSource = transformSource;
    }

    public void setModelVisible(final boolean modelVisible) {
        this.modelVisible = modelVisible;
    }

    /// The pose this frame draws with: the supplied one, else the animator's, else none — which draws
    /// the bind pose.
    private @Nullable Pose currentPose() {
        if (poseSource != null) return poseSource.get();

        return animator == null ? null : animator.getPose();
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        if (transformSource != null) armatureComponent.setLocalTransform(transformSource.get());

        if (poseSource == null && animator != null) {
            animator.update(a * 0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            armatureComponent.updateBoundTransforms();
        }

        if (!modelVisible) return;

        if (mesh != null) {
            poseStack.pushPose();
            poseStack.mulPose(armatureComponent.modelTransform().toMatrix());
            renderSkeletal(poseStack, byteBuffer, model3DViewer.getCameraPosition());
            poseStack.popPose();

            return;
        }

        if (vanillaRenderInfo != null && texture != null) {
            renderVanilla(poseStack, byteBuffer);
        }
    }

    /// Draws the mesh skinned when an armature and an animator are both installed, in its authored
    /// bind pose otherwise. A skinned draw goes to the compute shader where the device has one and
    /// to [SkeletalMesh#drawPosed] where it does not.
    private void renderSkeletal(PoseStack poseStack, ByteBufferBuilder byteBuffer, Vector3fc eye) {
        assert mesh != null;

        SkeletalMesh skMesh = mesh.getOrHandleException(Akythera.LOGGER::error);
        if (skMesh == null) return;

        Identifier supplied = previewTexture.get();
        Identifier drawTexture = supplied != null ? supplied : MissingTextureAtlasSprite.getLocation();
        AssetAccessor<? extends Armature> armatureAccessor = armature;
        Armature arm = armatureAccessor == null ? null : armatureAccessor.getOrHandleException(Akythera.LOGGER::error);
        Pose pose = currentPose();

        if (arm == null || pose == null) {
            drawBindPose(skMesh, drawTexture, poseStack, byteBuffer);
            return;
        }

        if (skMesh.getAnimationComputeShader() != null) {
            PatchModelRenderer.getInstance().drawSkinnedMeshImmediate(
                skMesh,
                armatureAccessor,
                pose,
                RenderTypes.entityTranslucent(drawTexture),
                poseStack.last(),
                Model3DViewer.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                NO_TINT,
                eye
            );

            return;
        }

        if (boundTransforms.length < arm.getBoneCount()) {
            boundTransforms = Matrix4fUtils.allocateMatrixArray(arm.getBoneCount());
        }

        pose.getForwardBoundTransforms(armatureAccessor, boundTransforms);
        arm.boundBackwardTransform(boundTransforms);

        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);

        skMesh.drawPosed(
            poseStack, builder,
            Mesh.DrawingFunction.NEW_ENTITY,
            Model3DViewer.FULL_BRIGHT,
            1.0F, 1.0F, 1.0F, 1.0F,
            OverlayTexture.NO_OVERLAY,
            boundTransforms
        );

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(drawTexture), meshData, true);
        }
    }

    /// The mesh as authored, no bone read. What a mesh with no armature or no animator shows.
    private void drawBindPose(SkeletalMesh skMesh, Identifier drawTexture, PoseStack poseStack, ByteBufferBuilder byteBuffer) {
        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);

        skMesh.draw(
            poseStack.last(), builder,
            Mesh.DrawingFunction.NEW_ENTITY,
            Model3DViewer.FULL_BRIGHT,
            1.0F, 1.0F, 1.0F, 1.0F,
            OverlayTexture.NO_OVERLAY
        );

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(drawTexture), meshData, true);
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
                vanillaRenderInfo.entityModel.setupAnim(vanillaRenderInfo.renderState);
            }
        } else {
            vanillaRenderInfo.entityModel.resetPose();
        }

        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);

        vanillaRenderInfo.entityModel.renderToBuffer(poseStack, builder, Model3DViewer.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, NO_TINT);

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(texture), meshData);
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
            piglinRenderState.isBrute = entityType == EntityTypes.PIGLIN_BRUTE;
        } else if (renderState instanceof NautilusRenderState nautilusRenderState) {
            nautilusRenderState.variant = StaticRegistryAccess.getRegistryAccess().get(ZombieNautilusVariants.DEFAULT).orElseThrow().value();
        }

        // Pre-init armature here even tho not provided as a constructor parameter
        armature = ComponentsFactory.getImpl().getExternalArmatureMappingFor(entityType);

        if (armature != null && ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId())) {
            PlayerAnimationLibraryPlugin.bindPreviewAnimManager(renderState, this);
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
        rebuildArmatureComponent();
    }

    @Override
    public ArmatureComponent getArmatureComponent() {
        return armatureComponent;
    }

    // ***************************************************************
    // ComponentAttachable
    // ***************************************************************

    @Override
    public Map<String, ? extends PatchComponent> getComponents() {
        return Map.of(ARMATURE, armatureComponent);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT;
    }

    @Override
    public <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type) {
        return null;
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

    /// An [Animator] implementation for [Model3DViewer] that require no live entity.
    ///
    /// Ticks elapsed time forward each game tick and samples [AnimationClip#getPose] at that time,
    /// looping when the clip ends. [#getOwner] and [#getMontagePlayer] are not supported.
    ///
    /// Useful for previewer screens without bound entity in world, like selection modal.
    public static final class SimpleHeadlessAnimator implements EditorScrubAnimator {
        @Nullable
        private AssetAccessor<AnimationClip> animation;
        private @Nullable ArmatureComponent armatureComponent;
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

        @Override
        @Deprecated
        public void setSoftPause(final boolean paused) {
        }

        @Override
        @Deprecated
        public void setHardPause(final boolean paused) {
        }

        @Override
        @Deprecated
        public boolean isSoftPaused() {
            return false;
        }

        @Override
        @Deprecated
        public boolean isHardPaused() {
            return false;
        }

        @Override
        public <O> O getOwner() {
            throw new UnsupportedOperationException("SimpleHeadlessAnimator has no owner");
        }

        @Override
        public @Nullable ArmatureComponent getArmatureComponent() {
            return armatureComponent;
        }

        @Override
        public void setArmatureComponent(final ArmatureComponent armatureComponent) {
            this.armatureComponent = armatureComponent;
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

    /// The vanilla model and the render state it is posed through, held together because a draw
    /// needs both. Not generic: the pair is only ever read as a pair, and reading two fields off a
    /// `VanillaRenderInfo<?>` captures them independently, which [EntityModel#setupAnim] refuses.
    private record VanillaRenderInfo(EntityModel<LivingEntityRenderState> entityModel, LivingEntityRenderState renderState) {
    }
}
