package com.yesman.akythera.client.api.component;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/// A component that draws itself in its owner's render pass.
///
/// [#submit] takes what [RenderLayer#submit] takes, so a vanilla layer's body ports onto it unchanged.
/// [PatchModelRenderer] collects every renderable under a patch on the extract pass and submits
/// each of them once.
public interface RenderableComponent<S extends EntityRenderState> extends PatchComponent {
    /// The render state [#extract] and [#submit] read. A component is skipped when the entity being
    /// drawn does not render through one.
    Class<S> stateType();

    /// Copies whatever this component draws off the entity, on the extract pass.
    default void extract(final EntityPatch<?> patch, final S state, final float partialTick) {
    }

    void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final S state,
        final float yRot,
        final float xRot
    );
}
