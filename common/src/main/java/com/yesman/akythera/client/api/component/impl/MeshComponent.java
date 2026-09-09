package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.Nullable;

/// A [Mesh] drawn wherever its host places it — under a [BoneSocket], it rides that bone.
///
/// Which mesh is a script decision: [#meshGetter] is a tree over [#owner], the patch this component
/// was instantiated for.
///
/// Instantiated per host by [MeshComponentBlueprint], so a mesh can grow per-instance state its
/// blueprint does not carry.
public record MeshComponent(@Nullable PureNode meshGetter, @Nullable EntityPatch<?> owner)
    implements RenderableComponent<LivingEntityRenderState>
{
    /// The context local [#submit] binds [#owner] to before running [#meshGetter]. Referenced by the
    /// editor holder's `@EditorNodeGraph` declaration — must stay a compile-time constant.
    public static final String OWNER_PARAM = "owner";

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

        if (modelSubmit == null || meshGetter == null || owner == null) return;

        ScriptVariables locals = new ScriptVariables();
        locals.setValue(OWNER_PARAM, owner);

        Object value = meshGetter.execute(null, new ScriptNode.ExecutionContext(locals));
        Mesh<?> resolved = value instanceof AssetAccessor<?> accessor ? (Mesh<?>) accessor.get() : null;

        if (resolved == null) return;

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
