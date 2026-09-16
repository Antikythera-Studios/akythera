package com.yesman.akythera.core.networking.payload;

import com.yesman.akythera.core.networking.PayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/// A payload's type, codec and handler.
///
/// The handler arrives as a [Supplier] because a dedicated server registers client bound payload
/// *types* without ever handling them, and resolving a client-only handler class there is fatal on
/// Fabric.
public record PayloadChannel<T extends CustomPacketPayload>(
    CustomPacketPayload.Type<T> type,
    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec,
    Supplier<BiConsumer<T, PayloadContext>> handler
) {
}
