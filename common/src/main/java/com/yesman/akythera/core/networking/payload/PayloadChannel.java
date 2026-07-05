package com.yesman.akythera.core.networking.payload;

import com.yesman.akythera.core.networking.PayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.BiConsumer;

public record PayloadChannel<T extends CustomPacketPayload>(
    CustomPacketPayload.Type<T> type,
    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec,
    BiConsumer<T, PayloadContext> handler
) {
}
