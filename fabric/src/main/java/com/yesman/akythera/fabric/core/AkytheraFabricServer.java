package com.yesman.akythera.fabric.core;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.fabric.client.core.networking.ClientBoundNetworkReceiverRegistrar;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class AkytheraFabricServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        // Dedicate server only holds payload types to encode them. No receivers are required.
        ClientBoundNetworkReceiverRegistrar.registerTypes();

        Akythera.onModInitializedInDedicatedServer();
    }
}
