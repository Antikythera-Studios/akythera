package com.yesman.akythera.client.core.networking;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.networking.PayloadContext;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundDataBlockSync;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlockSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;

public interface AkytheraClientBoundPayloadHandler {
    /// Receiver is always a [LocalPlayer] for client bound payloads
    static LocalPlayer getLocalPlayer(PayloadContext context) {
        return (LocalPlayer)context.receiver();
    }

    static void handleBlockSync(ClientBoundDataBlockSync payload, PayloadContext context) {
        // On an integrated server the reload listener already ingested into the shared static
        // manager; re-ingesting from the client thread would race it.
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }

        EnumerableDataBlockSync.ingest(payload.dataBlockType(), payload.entries());
    }

    static void handlePlayMontage(ClientBoundPlayAnimMontage payload, PayloadContext context) {
        AkytheraPatches.getEntityPatchOptional(context.receiver()).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() == null) return;

            entityPatch.getArmatureComponent().getAnimator().playAnimMontage(
                ResourceKey.create(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, payload.montageId()),
                payload.montageSection()
            );
        });
    }
}
