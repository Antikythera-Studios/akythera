package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/// A [Mesh] drawn wherever its host places it — under a [BoneSocket], it rides that bone.
///
/// Which mesh is a script decision, see [MeshGetter].
///
/// Instantiated per host by [MeshComponentBlueprint], so a mesh can grow per-instance state its
/// blueprint does not carry.
public final class MeshComponent implements RenderableComponent<LivingEntityRenderState> {
    private final MeshGetter meshGetter;

    public MeshComponent(final MeshGetter meshGetter) {
        this.meshGetter = meshGetter;
    }

    public MeshGetter getMeshGetter() {
        return meshGetter;
    }

    @Override
    public Class<LivingEntityRenderState> stateType() {
        return LivingEntityRenderState.class;
    }

    /// Drawn with the render type the entity's own model resolved to, since a mesh names no texture.
    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        EntityModelComponent.ModelSubmit modelSubmit =
            ((LivingEntityRenderStateAccessor) state).akythera$getModelSubmit();
        AssetAccessor<? extends Mesh<?>> accessor = meshGetter.resolve();
        Mesh<?> resolved = accessor == null ? null : accessor.get();

        if (modelSubmit == null || resolved == null) return;

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            modelSubmit.renderType(),
            (pose, buffer) -> resolved.draw(
                pose,
                buffer,
                Mesh.DrawingFunction.NEW_ENTITY,
                lightCoords,
                1.0F,
                1.0F,
                1.0F,
                1.0F,
                OverlayTexture.NO_OVERLAY
            )
        );
    }
}
