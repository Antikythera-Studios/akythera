package com.yesman.akythera.neoforged.client.core;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import com.yesman.akythera.client.core.AkytheraClient;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akythera.platform.neoforged.NeoForgedModPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

@Mod(value = Akythera.MODID, dist = Dist.CLIENT)
public final class AkytheraNeoForgedClient {
    public AkytheraNeoForgedClient(IEventBus modEventBus, ModContainer modContainer) {
        // The same instance the main entry point registered its listeners on;
        // a second instance would receive the client events with empty listener maps
        NeoForgedModPlatform neoForgedModPlatform = (NeoForgedModPlatform) ModPlatformProvider.get();
        AkytheraClient.onModConstructed();

        // Register mod bus events
        modEventBus.addListener(this::doClientStuff);
        modEventBus.addListener(this::registerClientReloadListener);
        modEventBus.addListener(this::modConfigLoadingEvent);
        neoForgedModPlatform.addClientListeners(modEventBus);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        AkytheraClient.onModInitialized();
    }

    private void registerClientReloadListener(final AddClientReloadListenersEvent event) {
        event.addListener(Akythera.identifier("mesh_manager_dynamic"), MeshManager.getInstance());
        event.addListener(Akythera.identifier("anim_manager_dynamic"), AnimationManager.getInstance());
        event.addListener(Akythera.identifier("armature_manager_dynamic"), ArmatureManager.getInstance());
    }

    private void modConfigLoadingEvent(final ModConfigEvent.Loading event) {
        // Drop if not a client config event
        if (!ModConfig.Type.CLIENT.equals(event.getConfig().getType())) return;

        AkytheraClientConfig.initConfigValues();
    }
}
