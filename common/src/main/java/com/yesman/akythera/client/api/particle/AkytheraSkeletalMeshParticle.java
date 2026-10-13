package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.SkeletalMeshParticleBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// A skinned 3D model a [SkeletalMeshParticleBlueprint] describes, posed by its own [ArmatureComponent] or by a pose
/// copied off the entity its system is attached to. With neither, it draws the model as authored.
@ScriptType
public class AkytheraSkeletalMeshParticle extends AkytheraStaticMeshParticle implements ComponentAttachable {
    /// Name of the [ArmatureComponent] in [#getComponents].
    public static final String ARMATURE = "armature";

    /// Name of the [ParticleMovementComponent] in [#getComponents].
    public static final String MOVEMENT = "movement";

    private final Map<String, PatchComponent> components = new LinkedHashMap<>();
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;
    /// The particle's own armature, or null when the blueprint names none.
    private final @Nullable ArmatureComponent armature;

    /// The pose the attached entity held when this particle spawned, or null when [#armature] poses the model.
    private final @Nullable Pose copiedPose;

    /// The armature the model is skinned by: [#armature]'s, else the attached entity's a [#copiedPose] came from. Null
    /// when there is neither, which draws the model unposed.
    private final @Nullable AssetAccessor<? extends Armature> skeleton;

    /// @throws IllegalStateException when the blueprint's mesh is no skinned mesh
    public AkytheraSkeletalMeshParticle(final AkytheraParticleEmitter emitter, final SkeletalMeshParticleBlueprint blueprint) {
        super(
            emitter,
            blueprint.properties(),
            blueprint.mesh(),
            blueprint.texture().orElse(null),
            blueprint.color(),
            emitter.getWorldTransform().mul(blueprint.transform())
        );

        if (!(blueprint.mesh().getOrHandleException(Akythera.LOGGER::error) instanceof SkeletalMesh)) {
            throw new IllegalStateException("A skeletal mesh particle names a mesh that is no skinned mesh");
        }

        // The animator looks its armature up through this host, so both are listed before it runs
        components.put(MOVEMENT, new ParticleMovementComponent(this));
        armature = blueprint.armature().map(armatureBlueprint -> armatureBlueprint.instantiate(this)).orElse(null);

        if (armature != null) components.put(ARMATURE, armature);

        uniqueComponents = ComponentAttachable.indexUnique(components);

        ParticleAttachment attachment = emitter.getSystem().getAttachment();
        EntityPatch<?> attached = blueprint.copyAttachedPose() && attachment != null
            ? AkytheraPatches.getEntityPatch(level.getEntity(attachment.entityId()))
            : null;
        ArmatureComponent attachedArmature = attached == null ? null : attached.getArmatureComponent();

        copiedPose = attachedArmature == null || attachedArmature.getCachedPose() == null ? null : new Pose(attachedArmature.getCachedPose());
        skeleton = armature != null ? armature.getArmature() : copiedPose != null ? attachedArmature.getArmature() : null;
    }

    @Override
    public void tick() {
        super.tick();

        if (!isAlive()) return;

        components.values().forEach(component -> {
            if (component.isEnabled() && component instanceof TickableComponent tickable) tickable.tick();
        });
    }

    @Override
    protected void submitMesh(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final Mesh<?> resolved,
        final RenderType renderType,
        final int lightCoords,
        final float partialTick
    ) {
        if (skeleton == null) {
            super.submitMesh(poseStack, submitNodeCollector, resolved, renderType, lightCoords, partialTick);
            return;
        }

        SkeletalMesh skeletalMesh = (SkeletalMesh)resolved;
        Armature resolvedArmature = skeleton.getOrHandleException(Akythera.LOGGER::error);

        if (resolvedArmature == null) return;

        Pose pose = drawnPose();
        RGBA color = state().getColor();

        if (skeletalMesh.getAnimationComputeShader() != null) {
            PatchModelRenderer.getInstance().submitSkinnedMesh(
                skeletalMesh, skeleton, pose, renderType, poseStack.last(),
                lightCoords, OverlayTexture.NO_OVERLAY, ARGB.colorFromFloat(color.a(), color.r(), color.g(), color.b()), 0
            );

            return;
        }

        Matrix4f[] boundTransforms = pose.getForwardBoundTransforms(skeleton, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount()));
        resolvedArmature.boundBackwardTransform(boundTransforms);

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (drawnPose, buffer) -> {
                // SkeletalMesh#drawPosed reads a pose stack; this one carries the submitted pose alone
                PoseStack skinningStack = new PoseStack();
                skinningStack.last().pose().set(drawnPose.pose());
                skinningStack.last().normal().set(drawnPose.normal());

                skeletalMesh.drawPosed(
                    skinningStack, buffer, Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY), lightCoords,
                    color.r(), color.g(), color.b(), color.a(), OverlayTexture.NO_OVERLAY, boundTransforms
                );
            }
        );
    }

    @Override
    protected void drawMeshImmediate(
        final PoseStack poseStack, final ByteBufferBuilder byteBuffer, final Mesh<?> resolved, final RenderType renderType, final int color, final Vector3fc eye
    ) {
        if (skeleton == null) {
            super.drawMeshImmediate(poseStack, byteBuffer, resolved, renderType, color, eye);
            return;
        }

        SkeletalMesh skeletalMesh = (SkeletalMesh)resolved;
        Armature resolvedArmature = skeleton.getOrHandleException(Akythera.LOGGER::error);

        if (resolvedArmature == null) return;

        Pose pose = drawnPose();

        if (skeletalMesh.getAnimationComputeShader() != null) {
            PatchModelRenderer.getInstance().drawSkinnedMeshImmediate(
                skeletalMesh, skeleton, pose, renderType, poseStack.last(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, eye
            );

            return;
        }

        Matrix4f[] boundTransforms = pose.getForwardBoundTransforms(skeleton, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount()));
        resolvedArmature.boundBackwardTransform(boundTransforms);

        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);

        skeletalMesh.drawPosed(
            poseStack, builder, Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY), LightCoordsUtil.FULL_BRIGHT,
            ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color), ARGB.alphaFloat(color), OverlayTexture.NO_OVERLAY, boundTransforms
        );

        drawSorted(renderType, builder.build(), eye);
    }

    /// The pose the model is drawn in: the copied one, else the armature's, else none.
    private Pose drawnPose() {
        Pose pose = copiedPose != null ? copiedPose : armature == null ? null : armature.getCachedPose();

        return pose == null ? Pose.EMPTY_POSE : pose;
    }

    // ***************************************************************
    // ComponentAttachable
    // ***************************************************************

    @Override
    public Map<String, PatchComponent> getComponents() {
        return components;
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT;
    }

    @SuppressWarnings("unchecked") @Override
    public <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type) {
        UniqueComponent component = uniqueComponents.get(type);

        return component != null && component.isEnabled() ? (C)component : null;
    }
}
