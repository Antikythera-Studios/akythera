package com.yesman.akythera.fabric.core;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.fabric.core.networking.FabricNetworkingHelper;
import com.yesman.akythera.fabric.core.networking.ServerBoundNetworkReceiverRegistrar;
import com.yesman.akythera.platform.fabric.FabricModPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class AkytheraFabric implements ModInitializer {
    public AkytheraFabric() {
        Akythera.initializePlatform(new FabricModPlatform());
        Akythera.onModConstructed();

        // Defines an entry point for addons to register their custom entry to ComponentsFactory
        FabricLoader.getInstance()
            .getEntrypointContainers("component_factory_initializer", ComponentFactoryInitializer.class)
            .forEach(container -> {
                try {
                    container.getEntrypoint().onFactoryInitialized(ComponentsFactory.getImpl());
                } catch (Exception e) {
                    Akythera.LOGGER.error(
                        "{} failed at register objects to ComponentsFactory",
                        container.getProvider().getMetadata().getId(),
                        e
                    );
                }
            });
    }

    @Override
    public void onInitialize() {
        Akythera.onModInitialized();
        NetworkingHelper.initialize(new FabricNetworkingHelper());

        // Initialize networking payload types & receivers
        ServerBoundNetworkReceiverRegistrar.registerTypes();
        ServerBoundNetworkReceiverRegistrar.registerReceivers();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            Akythera.onRegisterCommands(dispatcher);
        });
    }
}
