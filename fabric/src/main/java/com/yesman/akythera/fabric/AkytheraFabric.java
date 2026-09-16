package com.yesman.akythera.fabric;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityTrackingEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.fabric.initializer.ComponentFactoryInitializer;
import com.yesman.akythera.fabric.initializer.EnumerableDataBlockInitializer;
import com.yesman.akythera.fabric.networking.FabricNetworkingHelper;
import com.yesman.akythera.fabric.networking.ServerBoundNetworkReceiverRegistrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class AkytheraFabric implements ModInitializer {
    public AkytheraFabric() {
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

        Akythera.registerBuiltInDataBlockClasses();

        // Defines an entry point for addons to register enumerable data block enum classes
        FabricLoader.getInstance()
            .getEntrypointContainers("enumerable_data_block_initializer", EnumerableDataBlockInitializer.class)
            .forEach(container -> {
                try {
                    container.getEntrypoint().registerEnums();
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
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Akythera.onRegisterCommands(dispatcher));

        registerTrackingEvents();
    }

    /// Posts [BuiltInEventHooks.Entity#TRACKING_START] and `TRACKING_END`, which the engine declares
    /// and listens to but no loader fired.
    private static void registerTrackingEvents() {
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) ->
            AkytheraPatches.getEntityPatchOptional(trackedEntity).ifPresent(entityPatch ->
                BuiltInEventHooks.Entity.TRACKING_START.postWithListener(
                    new EntityTrackingEvent.Start(entityPatch, player),
                    entityPatch.getEntityEventListener()
                )
            )
        );

        EntityTrackingEvents.STOP_TRACKING.register((trackedEntity, player) ->
            AkytheraPatches.getEntityPatchOptional(trackedEntity).ifPresent(entityPatch ->
                BuiltInEventHooks.Entity.TRACKING_END.postWithListener(
                    new EntityTrackingEvent.Stop(entityPatch, player),
                    entityPatch.getEntityEventListener()
                )
            )
        );
    }
}
