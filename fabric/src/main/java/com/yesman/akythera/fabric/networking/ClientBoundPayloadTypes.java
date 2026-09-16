package com.yesman.akythera.fabric.networking;

import com.yesman.akythera.core.networking.payload.AkytheraPayloadTypes;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.stream.Stream;

/// Registers the client bound payload types on both sides.
///
/// Separate from the receiver registrar because a dedicated server has to know these types to
/// encode them, and touching anything under `fabric.client` there fails class loading outright.
@SuppressWarnings("unchecked")
public final class ClientBoundPayloadTypes {
    private ClientBoundPayloadTypes() {}

    public static void registerTypes() {
        Stream.concat(AkytheraPayloadTypes.CLIENT_BOUND.stream(), AkytheraPayloadTypes.BI_DIRECTIONAL.stream())
            .forEach(payloadChannel ->
                PayloadTypeRegistry.clientboundPlay().register(
                    (CustomPacketPayload.Type<CustomPacketPayload>)payloadChannel.type(),
                    (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>)payloadChannel.streamCodec()
                )
            );
    }
}
