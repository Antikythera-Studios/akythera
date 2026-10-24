package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Stops the rag doll of the entity's first armature on a client, see [ArmatureComponent#stopRagdoll].
@NullMarked
public record ClientBoundStopRagdoll(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundStopRagdoll> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "stop_ragdoll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundStopRagdoll> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientBoundStopRagdoll::entityId,
            ClientBoundStopRagdoll::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
