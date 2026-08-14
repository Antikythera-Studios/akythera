package com.yesman.akythera.fabric.networking;

import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.util.ClientOnly;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class FabricNetworkingHelper extends NetworkingHelper {
    private static MinecraftServer currentServer;

    public FabricNetworkingHelper() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> currentServer = null);
    }

    @ClientOnly
    @Override
    public void sendToServer(CustomPacketPayload payload, CustomPacketPayload... others) {
        ClientPlayNetworking.send(payload);
        for (CustomPacketPayload extraPayload : others) ClientPlayNetworking.send(extraPayload);
    }

    @Override
    public void sendToClient(ServerPlayer serverPlayer, CustomPacketPayload payload, CustomPacketPayload... others) {
        ServerPlayNetworking.send(serverPlayer, payload);
        for (CustomPacketPayload extraPayload : others) ServerPlayNetworking.send(serverPlayer, extraPayload);
    }

    @Override
    public void sendToAll(CustomPacketPayload payload, CustomPacketPayload... others) {
        PlayerLookup.all(currentServer).forEach(player -> {
            ServerPlayNetworking.send(player, payload);
            for (CustomPacketPayload extraPayload : others) ServerPlayNetworking.send(player, extraPayload);
        });
    }

    @Override
    public void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... others) {
        PlayerLookup.tracking(entity).forEach(player -> {
            ServerPlayNetworking.send(player, payload);
            for (CustomPacketPayload extraPayload : others) ServerPlayNetworking.send(player, extraPayload);
        });
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel serverLevel, ChunkPos chunkPos, CustomPacketPayload payload, CustomPacketPayload... others) {
        PlayerLookup.tracking(serverLevel, chunkPos).forEach(player -> {
            ServerPlayNetworking.send(player, payload);
            for (CustomPacketPayload extraPayload : others) ServerPlayNetworking.send(player, extraPayload);
        });
    }
}
