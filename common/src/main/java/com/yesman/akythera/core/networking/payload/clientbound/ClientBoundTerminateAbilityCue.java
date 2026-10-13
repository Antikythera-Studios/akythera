package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// One cue play the server ended on an entity: canceled, or run out when `cancelled` is false.
@NullMarked
public record ClientBoundTerminateAbilityCue(int entityId, int cueId, boolean cancelled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundTerminateAbilityCue> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "terminate_ability_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundTerminateAbilityCue> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundTerminateAbilityCue::entityId,
            ByteBufCodecs.VAR_INT,
            ClientBoundTerminateAbilityCue::cueId,
            ByteBufCodecs.BOOL,
            ClientBoundTerminateAbilityCue::cancelled,
            ClientBoundTerminateAbilityCue::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
