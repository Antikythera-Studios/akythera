package com.yesman.akythera.client.core.networking;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.networking.PayloadContext;
import com.yesman.akythera.core.networking.payload.bidirectional.BiDirectionalReplaceAnimation;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;

public interface AkytheraBiDirectionalPayloadHandler {
    static void handleReplaceAnimation(BiDirectionalReplaceAnimation payload, PayloadContext context) {
        AkytheraPatches.getEntityPatchOptional(context.receiver()).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() == null
                || !(entityPatch.getArmatureComponent().getAnimator() instanceof BlendingAnimator blendingAnimator)) {
                return;
            }

            if (payload.reset()) blendingAnimator.resetAllInterfaces();

            RegistryAccess registryAccess = entityPatch.getLevel().registryAccess();

            payload.entries().forEach(replaceEntry -> {
                Holder.Reference<? extends PlayableAnimation> animation =
                    registryAccess.getOrThrow(replaceEntry.animResourceKey());

                blendingAnimator.replaceAnimation(replaceEntry.interfaceKey(), animation);
            });
        });
    }
}
