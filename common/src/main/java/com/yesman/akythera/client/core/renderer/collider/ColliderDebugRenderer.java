package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.client.api.component.impl.EquipmentComponent;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Draws a patch's colliders in the F3+B hitbox scene.
///
/// Mirrors [PatchModelRenderer]'s two passes: [#collect] runs where the entity is in hand, [#submit]
/// where the pose stack is.
public final class ColliderDebugRenderer {
    private ColliderDebugRenderer() {}

    private static final int COLOR = 0xFF33FF66;

    /// One collider to draw, and the [BoneSocket] placing it. `socket` is null for a collider hanging
    /// straight off the patch root.
    public record ColliderSubmit(Collider collider, @Nullable BoneSocket socket) {}

    /// Whether the player has the hitbox scene open.
    public static boolean isEnabled() {
        return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    /// Every collider under `patch`, plus those of the items its [EquipmentComponent]s draw.
    public static void collect(final EntityPatch<?> patch, final List<ColliderSubmit> out) {
        collect(patch, null, patch.getOriginal() instanceof LivingEntity living ? living : null, out);
    }

    private static void collect(
        final ComponentAttachable host,
        final @Nullable BoneSocket socket,
        final @Nullable LivingEntity holder,
        final List<ColliderSubmit> out
    ) {
        for (PatchComponent component : host.getComponents().values()) {
            if (component instanceof ColliderComponent<?> collider) {
                out.add(new ColliderSubmit(collider.getCollider(), socket));
            }

            // A weapon's collider is authored on its item patch, which no entity component tree holds.
            // The socket drawing the item is where it belongs on screen.
            if (component instanceof EquipmentComponent equipment && socket != null && holder != null) {
                ItemPatch itemPatch = AkytheraPatches.getItemPatch(holder.getItemBySlot(equipment.equipmentSlot()));

                if (itemPatch != null) {
                    collect(itemPatch, socket, holder, out);
                }
            }

            if (component instanceof ComponentAttachable attachable) {
                collect(attachable, component instanceof BoneSocket boneSocket ? boneSocket : socket, holder, out);
            }
        }
    }

    public static void submit(
        final List<ColliderSubmit> submits, final PoseStack poseStack, final SubmitNodeCollector collector
    ) {
        for (ColliderSubmit submit : submits) {
            poseStack.pushPose();

            if (submit.socket() != null) {
                RenderUtils.toArmatureSpace(poseStack);
                poseStack.mulPose(submit.socket().getBoundTransform().toMatrix());
            }

            collector.submitCustomGeometry(
                poseStack,
                RenderTypes.lines(),
                (pose, buffer) -> ColliderRenderers.emitWireframe(buffer, pose, submit.collider(), COLOR)
            );

            poseStack.popPose();
        }
    }
}
