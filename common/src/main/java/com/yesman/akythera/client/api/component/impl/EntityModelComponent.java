package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// The renderable that draws the patched entity's own model, with a [SkeletalMesh] in place of the
/// vanilla one. Which mesh is a script decision, see [MeshGetter].
///
/// If Player Animation Library is installed, you can skip this component
/// and let [LivingEntityRenderer] draws it.
///
/// Simultaneously a component and a blueprint: [#instantiate] copies the authored component per
/// patch so its copy's [#meshGetter] reads that patch, the way [BoneSocket] copies itself per
/// armature.
public record EntityModelComponent(MeshGetter meshGetter, Renderer renderer)
    implements
        RenderableComponent<LivingEntityRenderState>,
        UniqueComponent,
        ComponentBlueprint<EntityModelComponent, EntityPatch<Entity>>
{
    /// The getter is optional: a [Renderer#PLAYER_ANIMATION_LIBRARY] component names no mesh. The
    /// decoded component is the authored one, so its getter is unbound until [#instantiate].
    public static final MapCodec<EntityModelComponent> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                PureNode.CODEC.optionalFieldOf("mesh_getter")
                    .forGetter(ParseUtils.provideOptional(component -> component.meshGetter().node())),
                StringRepresentable.fromEnum(Renderer::values).fieldOf("renderer").forGetter(EntityModelComponent::renderer)
            )
            .apply(instance, (meshGetter, renderer) -> new EntityModelComponent(new MeshGetter(meshGetter.orElse(null), null), renderer))
    );

    /// The mesh to skin, or null whenever the vanilla submit should stand: a renderer that resolves
    /// to Player Animation Library, no mesh the getter resolves to, a mesh that is not skeletal, or a
    /// device with no compute support to skin it with.
    public @Nullable SkeletalMesh resolveSkeletalMesh() {
        boolean skinnedMesh = switch (renderer) {
            case SKELETAL_MESH -> true;
            case PLAYER_ANIMATION_LIBRARY -> false;
            case DEFAULT -> AkytheraClientConfig.getDefaultAnimationDriver()
                == AkytheraClientConfig.DefaultAnimationDriver.SKINNED_MESH;
        };

        if (!skinnedMesh || !ClientComponentsFactory.getImpl().supportComputeShader()) {
            return null;
        }

        AssetAccessor<? extends Mesh<?>> mesh = meshGetter.resolve();

        return mesh != null && mesh.get() instanceof SkeletalMesh skeletalMesh ? skeletalMesh : null;
    }

    // ***************************************************************
    // RenderableComponent
    // ***************************************************************

    @Override
    public Class<LivingEntityRenderState> stateType() {
        return LivingEntityRenderState.class;
    }

    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        LivingEntityRenderStateAccessor stateAccessor = (LivingEntityRenderStateAccessor) state;
        ModelSubmit modelSubmit = stateAccessor.akythera$getModelSubmit();

        if (modelSubmit == null) return;

        SkeletalMesh skeletalMesh = resolveSkeletalMesh();
        Pose animationPose = stateAccessor.akythera$getPose();
        AssetAccessor<? extends Armature> armature = stateAccessor.akythera$getArmature();

        if (skeletalMesh == null || animationPose == null || armature == null) {
            modelSubmit.submitVanillaModel(submitNodeCollector, poseStack, lightCoords, state);
            return;
        }

        poseStack.pushPose();
        RenderUtils.toArmatureSpace(poseStack);

        PatchModelRenderer.getInstance().submitSkinnedMesh(
            skeletalMesh,
            armature,
            animationPose,
            modelSubmit.renderType(),
            poseStack.last(),
            lightCoords,
            modelSubmit.overlayCoords(),
            modelSubmit.tintedColor(),
            state.outlineColor
        );

        poseStack.popPose();
    }

    // ***************************************************************
    // ComponentBlueprint
    // ***************************************************************

    /// A copy of the authored component whose [#meshGetter] reads `blueprintOwner`.
    @Override
    public EntityModelComponent instantiate(EntityPatch<Entity> blueprintOwner) {
        if (renderer() == Renderer.PLAYER_ANIMATION_LIBRARY && !ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId())) {
            throw new IllegalStateException("Entity Model Component requires PAL to be installed, but it doesn't.");
        }

        return new EntityModelComponent(new MeshGetter(meshGetter.node(), blueprintOwner), renderer);
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<EntityModelComponent, EntityPatch<Entity>>> mapCodec() {
        return CODEC;
    }

    public enum Renderer implements StringRepresentable {
        /// Follows the configuration. Users can decide which renderer to use.
        DEFAULT("default"),

        /// Force the model always rendered by PAL. This hard-checks whether
        /// PAL is installed in mod loader and throws an exception if the mod
        /// is not installed.
        ///
        /// There's no reason to author a mesh getter if using PAL.
        PLAYER_ANIMATION_LIBRARY("pal"),

        /// Force the model to use skeletal mesh.
        SKELETAL_MESH("skm");

        final String name;

        Renderer(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /// The arguments of the [SubmitNodeCollector#submitModel] call [LivingEntityRenderer#submit] makes
    /// for the entity's own model, less the light coords the renderables already receive and the
    /// outline color the state carries.
    public record ModelSubmit(
        Model<Object> model,
        RenderType renderType,
        int overlayCoords,
        int tintedColor,
        @Nullable TextureAtlasSprite sprite,
        ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        /// The vanilla draw, unaltered.
        public void submitVanillaModel(
            final SubmitNodeCollector submitNodeCollector,
            final PoseStack poseStack,
            final int lightCoords,
            final LivingEntityRenderState state
        ) {
            submitNodeCollector.submitModel(
                model,
                state,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                tintedColor,
                sprite,
                state.outlineColor,
                crumblingOverlay
            );
        }
    }
}
