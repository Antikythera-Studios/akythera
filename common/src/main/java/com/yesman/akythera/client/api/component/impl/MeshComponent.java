package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.physics.ClothPhysicsData;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.shader.backend.setup.ClothComputeShader;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/// A [Mesh] drawn wherever its host places it.
///
/// Under a [TransformSocket] it rides that socket. Under an [ArmatureComponent] it is the entity's
/// own model: the vanilla model and its [RenderLayer]s are dropped for it, and several meshes on one
/// armature are how those layers are rebuilt.
///
/// A [SkeletalMesh] is skinned by [#armature]—the armature this component hangs off, not the patch's
/// first—and sits in that armature's space, not its host's; one carrying [ClothPhysicsData] is
/// simulated on top of that. [PatchModelRenderer] places either kind at its [#modelTransform].
///
/// Instantiated per host by [MeshComponentBlueprint], so a mesh can grow per-instance state its
/// blueprint does not carry—a cloth's simulation is keyed by this instance, and so is its switch.
public final class MeshComponent implements RenderableComponent<LivingEntityRenderState>, TransformComponent {
    /// The context local [#submit] binds [#owner] to before running [#renderTypeGetter]. Referenced by
    /// the editor holder's `@EditorNodeGraph` declaration—must stay a compile-time constant.
    public static final String OWNER_PARAM = "owner";

    /// The [ScriptCaller] slot of [#renderTypeGetter], behind the component's path.
    public static final String RENDER_TYPE_SLOT = "renderTypeGetter";

    /// The mesh drawn, or null for a component that names none and draws nothing.
    private final @Nullable AssetAccessor<? extends Mesh<?>> mesh;

    private final @Nullable AssetAccessor<? extends Mesh<?>> slimMesh;

    /// A tree over [#owner] answering the [RenderType] to draw with; anything else, or no tree, draws
    /// with the entity's own model render type.
    private final @Nullable PureNode renderTypeGetter;

    /// The patch this component was instantiated for.
    private final @Nullable EntityPatch<?> owner;

    /// The armature posing this mesh, null where the host chain reaches none—a static mesh needs no
    /// armature, a skeletal one draws nothing without it.
    private final @Nullable ArmatureComponent armature;

    /// The host this component is attached under.
    private final @Nullable ComponentAttachable host;

    /// Where the mesh sits in its host's space, or in [#armature]'s for a skeletal mesh.
    private Transform localTransform;

    /// The elements of its host's space, or of [#armature]'s for a skeletal mesh, the mesh takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    private boolean enabled = true;

    /// Who runs [#renderTypeGetter], found on its first run: the owner's component tree is complete by
    /// then.
    private @Nullable ScriptCaller renderTypeCaller;

    /// Set once [#renderTypeGetter] threw. It is not run again.
    private boolean renderTypeFailed;

    public MeshComponent(
        final @Nullable AssetAccessor<? extends Mesh<?>> mesh,
        final @Nullable AssetAccessor<? extends Mesh<?>> slimMesh,
        final @Nullable PureNode renderTypeGetter,
        final @Nullable EntityPatch<?> owner,
        final @Nullable ArmatureComponent armature,
        final @Nullable ComponentAttachable host,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy
    ) {
        this.mesh = mesh;
        this.slimMesh = slimMesh;
        this.renderTypeGetter = renderTypeGetter;
        this.owner = owner;
        this.armature = armature;
        this.host = host;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    public @Nullable AssetAccessor<? extends Mesh<?>> getMesh() {
        if (slimMesh != null && owner instanceof PlayerPatch playerPatch) {
            PlayerSkin skin = playerPatch.getPlayerSkin();

            if (skin != null && skin.model() == PlayerModelType.SLIM) return slimMesh;
        }

        return mesh;
    }

    public @Nullable EntityPatch<?> getOwner() {
        return owner;
    }

    public @Nullable ArmatureComponent getArmature() {
        return armature;
    }

    /// Whether the mesh resolves to a [SkeletalMesh], which sits in [#armature]'s space.
    public boolean isSkeletal() {
        AssetAccessor<? extends Mesh<?>> drawn = getMesh();

        return drawn != null && drawn.getOrHandleException(Akythera.LOGGER::error) instanceof SkeletalMesh;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
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
        PatchModelRenderer.ModelDraw modelDraw =
            ((LivingEntityRenderStateAccessor) state).akythera$getModelDraw();

        AssetAccessor<? extends Mesh<?>> drawn = getMesh();

        if (modelDraw == null || drawn == null || owner == null) return;

        Mesh<?> resolved = drawn.get();

        if (resolved == null) return;

        RenderType renderType = resolveRenderType(modelDraw);

        if (!(resolved instanceof SkeletalMesh skeletalMesh)) {
            submitNodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, buffer) -> resolved.draw(
                    pose,
                    buffer,
                    Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY),
                    lightCoords,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    OverlayTexture.NO_OVERLAY
                )
            );

            return;
        }

        if (armature == null) return;

        Pose animationPose = armature.getCachedPose();
        AssetAccessor<? extends Armature> armatureAsset = armature.getArmature();

        if (animationPose == null || armatureAsset == null) return;

        ClothPhysicsData cloth = skeletalMesh.getClothPhysics();
        ClothComputeShader clothShader = skeletalMesh.getClothComputeShader();
        boolean computeSkinning = skeletalMesh.getAnimationComputeShader() != null;

        if (cloth != null && clothShader != null) {
            AssetAccessor<? extends ArmaturePhysicsData> bodies = cloth.armaturePhysics();

            PatchModelRenderer.getInstance().submitClothMesh(
                this,
                clothShader,
                armatureAsset,
                animationPose,
                bodies == null ? null : bodies.getOrHandleException(Akythera.LOGGER::error),
                cloth.parameters(),
                renderType,
                poseStack.last(),
                Minecraft.getInstance().gameRenderer.mainCamera().position(),
                lightCoords,
                modelDraw.overlayCoords(),
                modelDraw.tintedColor(),
                state.outlineColor
            );
        } else if (cloth != null) {
            // TODO: simulate under Iris once an Iris cloth transformer exists
            if (computeSkinning && ClientComponentsFactory.getImpl().getShaderBackend() == ClientComponentsFactory.ShaderBackend.IRIS) {
                PatchModelRenderer.getInstance().submitSkinnedMesh(
                    skeletalMesh, armatureAsset, animationPose, renderType, poseStack.last(),
                    lightCoords, modelDraw.overlayCoords(), modelDraw.tintedColor(), state.outlineColor
                );
            }
        } else if (computeSkinning) {
            PatchModelRenderer.getInstance().submitSkinnedMesh(
                skeletalMesh, armatureAsset, animationPose, renderType, poseStack.last(),
                lightCoords, modelDraw.overlayCoords(), modelDraw.tintedColor(), state.outlineColor
            );
        } else {
            Armature resolvedArmature = armatureAsset.get();
            Matrix4f[] boundTransforms = animationPose.getForwardBoundTransforms(
                armatureAsset, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount())
            );

            resolvedArmature.boundBackwardTransform(boundTransforms);

            submitNodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, buffer) -> {
                    // SkeletalMesh#drawPosed reads a pose stack; this one carries the submitted pose alone
                    PoseStack skinningStack = new PoseStack();
                    skinningStack.last().pose().set(pose.pose());
                    skinningStack.last().normal().set(pose.normal());

                    skeletalMesh.drawPosed(
                        skinningStack,
                        buffer,
                        Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY),
                        lightCoords,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        modelDraw.overlayCoords(),
                        boundTransforms
                    );
                }
            );
        }
    }

    /// The type this mesh draws with: what [#renderTypeGetter] answers, else the entity's own.
    private RenderType resolveRenderType(final PatchModelRenderer.ModelDraw modelDraw) {
        if (renderTypeGetter == null || renderTypeFailed) return modelDraw.renderType();

        if (renderTypeCaller == null) {
            renderTypeCaller = ScriptCaller.ofComponent(owner, this, RENDER_TYPE_SLOT);
        }

        ScriptVariables locals = new ScriptVariables();
        locals.setValue(OWNER_PARAM, owner);

        ScriptNode.ExecutionContext context = ScriptRecorder.newContext(renderTypeCaller, locals);

        try {
            Object picked = renderTypeGetter.execute(null, context);

            return picked instanceof RenderType pickedType ? pickedType : modelDraw.renderType();
        } catch (Throwable throwable) {
            renderTypeFailed = true;
            ScriptRecorder.recordFailure(context, throwable);

            return modelDraw.renderType();
        }
    }

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public @Nullable ComponentAttachable host() {
        return host;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }

    /// A skeletal mesh sits in its armature's space.
    @Override
    public Transform parentModelTransform() {
        return armature != null && isSkeletal() ? armature.modelTransform() : TransformComponent.super.parentModelTransform();
    }

    @Override
    public Transform previousParentModelTransform() {
        return armature != null && isSkeletal() ? armature.previousModelTransform() : TransformComponent.super.previousParentModelTransform();
    }
}
