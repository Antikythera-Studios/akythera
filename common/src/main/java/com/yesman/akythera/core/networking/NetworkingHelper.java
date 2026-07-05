package com.yesman.akythera.core.networking;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/// Since custom networking methods differ by mod loaders, the networking helper abstracts
/// each packet sending type and mod loader specific networking helpers are implemented in
/// each project.
///
/// The [CustomPacketPayload] varargs in each sending method will be bundled and sent in
/// [ClientboundCustomPayloadPacket].
public abstract class NetworkingHelper {
    private static NetworkingHelper instance;

    public static void initialize(NetworkingHelper networkingHelper) {
        if (instance != null) {
            Akythera.LOGGER.error("NetworkingHelper already initialized!");
        } else {
            instance = networkingHelper;
        }
    }

    public static NetworkingHelper getInstance() {
        return instance;
    }

    /// Send from a local client to a server
    @ClientOnly
    public abstract void sendToServer(CustomPacketPayload payload, CustomPacketPayload... others);

    /// Send from a server to a specific client
    public abstract void sendToClient(ServerPlayer serverPlayer, CustomPacketPayload payload, CustomPacketPayload... others);

    /// Send from a server to all logged in clients
    public abstract void sendToAll(CustomPacketPayload payload, CustomPacketPayload... others);

    /// Send from a server to all clients tracking a specific entity
    public abstract void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... others);

    /// Send from a server to all clients tracking a specific entity. If the entity is [ServerPlayer], payloads send
    /// to the player's client too.
    public void sendToPlayersTrackingEntityWithSelfIfPlayer(Entity entity, CustomPacketPayload payload, CustomPacketPayload... others) {
        if (entity instanceof ServerPlayer serverPlayer) sendToClient(serverPlayer, payload, others);
        sendToPlayersTrackingEntity(entity, payload, others);
    }

    /// Send from a server to all clients tracking a specific [ServerPlayer], including the player's client.
    public void sendToPlayersTrackingServerPlayer(ServerPlayer serverPlayer, CustomPacketPayload payload, CustomPacketPayload... others) {
        sendToClient(serverPlayer, payload, others);
        sendToPlayersTrackingEntity(serverPlayer, payload, others);
    }

    /// Send from a server to all clients tracking a specific chunk (restricted to players in a dimension)
    public abstract void sendToPlayersTrackingChunk(ServerLevel serverLevel, ChunkPos chunkPos, CustomPacketPayload payload, CustomPacketPayload... others);

    /// Optimize packets in one big payload instead of sending multiple times. Payloads
    /// are executed sequentially in the order in which [PayloadBundleBuilder#and] was called.
    public static class PayloadBundleBuilder {
        public static PayloadBundleBuilder create() {
            return new PayloadBundleBuilder();
        }

        public static PayloadBundleBuilder beginWith(CustomPacketPayload payload) {
            return new PayloadBundleBuilder().and(payload);
        }

        private final List<CustomPacketPayload> payloads = new ArrayList<>();

        public PayloadBundleBuilder and(CustomPacketPayload payload) {
            this.payloads.add(payload);
            return this;
        }

        public void send(BiConsumer<CustomPacketPayload, CustomPacketPayload[]> sendTo) {
            if (this.payloads.size() == 1) {
                sendTo.accept(this.payloads.getFirst(), new CustomPacketPayload[0]);
            } else if (!this.payloads.isEmpty()) {
                sendTo.accept(this.payloads.getFirst(), this.payloads.subList(1, this.payloads.size()).toArray(new CustomPacketPayload[0]));
            }
        }
    }
}
