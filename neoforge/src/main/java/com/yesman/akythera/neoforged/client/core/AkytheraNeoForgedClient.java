package com.yesman.akythera.neoforged.client.core;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.AkytheraClient;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.platform.neoforged.NeoForgedModPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

@Mod(value = Akythera.MODID, dist = Dist.CLIENT)
public final class AkytheraNeoForgedClient {
    public AkytheraNeoForgedClient(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgedModPlatform neoForgedModPlatform = new NeoForgedModPlatform();
        AkytheraClient.onModConstructed();

        // Register mod bus events
        modEventBus.addListener(this::doClientStuff);
        modEventBus.addListener(this::registerClientReloadListener);
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
}
