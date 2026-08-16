package com.yesman.akythera.client.gui.widget.viewer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import com.mojang.serialization.Codec;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.compat.playeranimlibrary.AkytheraAnimationLayer;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariants;
import net.minecraft.world.entity.animal.pig.PigVariants;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class EntityModelComponent implements Model3DViewerComponent, AnimatableObject {
    /// Vertex tint of every previewed mesh, ARGB.
    private static final int NO_TINT = 0xFFFFFFFF;

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
    private VanillaRenderInfo<?> vanillaRenderInfo;

    private final ArmatureComponent armatureComponent = new PreviewModelArmatureComponent();

    /// Scratch the bound skeleton is resolved into for the [SkeletalMesh#drawPosed] fallback.
    private Matrix4f[] boundTransforms = Matrix4fUtils.allocateMatrixArray(0);

    /// Mesh-only render for a skeletal mesh
    public EntityModelComponent(final @NonNull AssetAccessor<? extends SkeletalMesh> mesh) {
        this.mesh = mesh;
        this.texture = null;
        this.previewTexture = () -> sidecarTexture(mesh);
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

    /// Setup animation for [SimpleHeadlessAnimator]. `null` clears the clip while keeping the
    /// animator, so the model falls back to the bind pose ([SimpleHeadlessAnimator#getPose]) instead
    /// of freezing on the last one it was posed into.
    public void setSimplePlayingAnimation(final @Nullable AssetAccessor<AnimationClip> animClip) {
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
    public void setSimplePlayingAnimation(final @Nullable AssetAccessor<AnimationClip> animClip, boolean updatePose) {
        setSimplePlayingAnimation(animClip);

        if (animator instanceof SimpleHeadlessAnimator simpleHeadlessAnimator) {
            simpleHeadlessAnimator.setUpdatePose(updatePose);
        }
    }

    public void setArmature(final @NonNull AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        if (animator != null) {
            animator.update(a * 0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            armatureComponent.updateBoundTransforms();
        }

        if (mesh != null) {
            renderSkeletal(poseStack, byteBuffer);
            return;
        }

        if (vanillaRenderInfo != null && texture != null) {
            renderVanilla(poseStack, byteBuffer);
        }
    }

    /// Draws the mesh skinned when an armature and an animator are both installed, in its authored
    /// bind pose otherwise. A skinned draw goes to the compute shader where the device has one and
    /// to [SkeletalMesh#drawPosed] where it does not.
    private void renderSkeletal(PoseStack poseStack, ByteBufferBuilder byteBuffer) {
        assert mesh != null;

        SkeletalMesh skMesh = mesh.getOrHandleException(Akythera.LOGGER::error);
        if (skMesh == null) return;

        Identifier supplied = previewTexture.get();
        Identifier drawTexture = supplied != null ? supplied : MissingTextureAtlasSprite.getLocation();
        AssetAccessor<? extends Armature> armatureAccessor = armature;
        Armature arm = armatureAccessor == null ? null : armatureAccessor.getOrHandleException(Akythera.LOGGER::error);

        if (arm == null || animator == null) {
            drawBindPose(skMesh, drawTexture, poseStack, byteBuffer);
            return;
        }

        Pose pose = animator.getPose();

        if (skMesh.getAnimationComputeShader() != null) {
            PatchModelRenderer.getInstance().drawSkinnedMeshImmediate(
                skMesh,
                armatureAccessor,
                pose,
                RenderTypes.entityTranslucent(drawTexture),
                poseStack.last(),
                Model3DViewer.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                NO_TINT
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
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(drawTexture), meshData);
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
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(drawTexture), meshData);
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

    /// An [Animator] implementation for [Model3DViewer] that require no live entity.
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

            entityModel.setupAnim(renderState);
        }
    }
}
