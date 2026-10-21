package com.yesman.akythera.fabric.client.networking;

import com.yesman.akythera.core.networking.PayloadContext;
import com.yesman.akythera.core.networking.payload.AkytheraPayloadTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public final class ClientBoundNetworkReceiverRegistrar {
    /// Maps a payload handler to use [PayloadContext] instead [ClientPlayNetworking.Context]
    private static <T extends CustomPacketPayload> ClientPlayNetworking.PlayPayloadHandler<@NonNull T> mapContext(BiConsumer<T, PayloadContext> original) {
        return (payload, context) -> original.accept(payload, PayloadContext.createContext(context.player()));
    }

    // Register stream codecs
    public static void registerTypes() {
        Stream.concat(AkytheraPayloadTypes.CLIENT_BOUND.stream(), AkytheraPayloadTypes.BI_DIRECTIONAL.stream()).forEach(
            payloadChannel ->
                PayloadTypeRegistry.clientboundPlay().register(
                    (CustomPacketPayload.Type<CustomPacketPayload>)payloadChannel.type(),
                    (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>) payloadChannel.streamCodec()
                )
        );
    }

    // Register receivers
    public static void registerReceivers() {
        Stream.of(AkytheraPayloadTypes.CLIENT_BOUND, AkytheraPayloadTypes.COMMON_CLIENT_BOUND, AkytheraPayloadTypes.BI_DIRECTIONAL).flatMap(List::stream).forEach(
            payloadChannel ->
                ClientPlayNetworking.registerGlobalReceiver(
                    (CustomPacketPayload.Type<CustomPacketPayload>)payloadChannel.type(),
                    mapContext((BiConsumer<CustomPacketPayload, PayloadContext>)payloadChannel.handler().get())
                )
        );

        AkytheraPayloadTypes.COMMON_CLIENT_BOUND.forEach(payloadChannel -> {
            BiConsumer<CustomPacketPayload, PayloadContext> handler = (BiConsumer<CustomPacketPayload, PayloadContext>)payloadChannel.handler().get();

            ClientConfigurationNetworking.registerGlobalReceiver(
                (CustomPacketPayload.Type<CustomPacketPayload>)payloadChannel.type(),
                (payload, context) -> handler.accept(payload, PayloadContext.createContext(null))
            );
        });
    }

    private ClientBoundNetworkReceiverRegistrar() {}
}
