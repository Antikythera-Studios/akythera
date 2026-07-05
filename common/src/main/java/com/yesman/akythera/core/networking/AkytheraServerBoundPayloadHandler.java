package com.yesman.akythera.core.networking;

import net.minecraft.server.level.ServerPlayer;

public interface AkytheraServerBoundPayloadHandler {
    /// Receiver is always a [ServerPlayer] for client bound payloads
    static ServerPlayer getServerPlayer(PayloadContext context) {
        return (ServerPlayer)context.receiver();
    }
}
