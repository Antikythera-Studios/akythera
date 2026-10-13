package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.EquipmentSlotAnchor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Draws a patch's colliders in the F3+B hitbox scene.
///
/// Mirrors [PatchModelRenderer]'s two passes: [#collect] runs where the entity is in hand, [#submit]
/// where the pose stack is.
public final class ColliderDebugRenderer {
    private ColliderDebugRenderer() {}

    private static final int COLOR = 0xFF33FF66;

    /// One collider to draw, and its own transform in armature space.
    public record ColliderSubmit(Collider collider, Transform transform) {}

    /// Whether the player has the hitbox scene open.
    public static boolean isEnabled() {
        return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    /// Every collider under `patch`, plus those of the items its [EquipmentSlotAnchor]s draw.
    public static void collect(final EntityPatch<?> patch, final List<ColliderSubmit> out) {
        collect(patch, patch instanceof EquipmentHolder holder ? holder : null, out);
    }

    private static void collect(final ComponentAttachable host, final @Nullable EquipmentHolder holder, final List<ColliderSubmit> out) {
        for (PatchComponent component : host.getComponents().values()) {
            if (!component.isEnabled()) continue;

            if (component instanceof ColliderComponent<?> collider) {
                out.add(new ColliderSubmit(collider.getCollider(), collider.modelTransform()));
            }

            // A weapon's collider is authored on its item patch, which no entity component tree holds
            if (component instanceof EquipmentSlotAnchor anchor && holder != null) {
                ItemPatch itemPatch = holder.getItemPatchInSlot(anchor.equipmentSlot());

                if (itemPatch != null) collect(itemPatch, null, out);
            }

            if (component instanceof ComponentAttachable attachable) {
                collect(attachable, holder, out);
            }
        }
    }

    public static void submit(
        final List<ColliderSubmit> submits, final PoseStack poseStack, final SubmitNodeCollector collector
    ) {
        for (ColliderSubmit submit : submits) {
            poseStack.pushPose();
            RenderUtils.toArmatureSpace(poseStack);
            poseStack.mulPose(ColliderRenderers.placement(submit.collider(), submit.transform()));

            collector.submitCustomGeometry(
                poseStack,
                RenderTypes.lines(),
                (pose, buffer) -> ColliderRenderers.emitWireframe(buffer, pose, submit.collider(), COLOR)
            );

            poseStack.popPose();
        }
    }
}
