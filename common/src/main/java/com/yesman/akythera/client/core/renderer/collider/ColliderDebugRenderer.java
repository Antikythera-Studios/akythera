package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.animation.notify.impl.LivingHurtNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/// Draws a patch's colliders in the F3+B hitbox scene.
///
/// Mirrors [PatchModelRenderer]'s two passes: [#collect] runs where the entity is in hand, [#submit]
/// where the pose stack is.
public final class ColliderDebugRenderer {
    private ColliderDebugRenderer() {}

    private static final int COLOR = 0xFF33FF66;
    private static final int STRIKING_COLOR = 0xFFFF3333; // under the socket of a LivingHurtNotifyState in its window

    /// One collider to draw, its own transform in armature space and its wireframe color.
    public record ColliderSubmit(Collider collider, Transform transform, int color) {}

    /// Whether the player has the hitbox scene open.
    public static boolean isEnabled() {
        return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    /// Every collider under `patch`, the ones an [EquipmentSlotBinder] attached for its item included. The colliders
    /// a playing montage's [LivingHurtNotifyState] strikes with take [#STRIKING_COLOR].
    public static void collect(final EntityPatch<?> patch, final List<ColliderSubmit> out) {
        Set<ColliderComponent> striking = Collections.newSetFromMap(new IdentityHashMap<>());
        ArmatureComponent armature = patch.getArmatureComponent();

        if (armature != null) {
            for (AnimationMontagePlayer montagePlayer : armature.getAnimator().getMontagePlayers()) {
                for (LivingHurtNotifyState hurt : montagePlayer.getActiveNotifyStates(LivingHurtNotifyState.class)) {
                    if (patch.getComponentAt(hurt.socket()) instanceof ComponentAttachable host) {
                        striking.addAll(host.findComponentsBelow(ColliderComponent.class));
                    }
                }
            }
        }

        for (ColliderComponent<?> collider : patch.findComponentsBelow(ColliderComponent.class)) {
            out.add(new ColliderSubmit(collider.getCollider(), collider.modelTransform(), striking.contains(collider) ? STRIKING_COLOR : COLOR));
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
                (pose, buffer) -> ColliderRenderers.emitWireframe(buffer, pose, submit.collider(), submit.color())
            );

            poseStack.popPose();
        }
    }
}
