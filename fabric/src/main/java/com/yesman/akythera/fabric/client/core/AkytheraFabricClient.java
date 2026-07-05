package com.yesman.akythera.fabric.client.core;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.AkytheraClient;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.fabric.client.core.networking.ClientBoundNetworkReceiverRegistrar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public final class AkytheraFabricClient implements ClientModInitializer {
    public AkytheraFabricClient() {
        AkytheraClient.onModConstructed();
    }

    @Override
    public void onInitializeClient() {
        AkytheraClient.onModInitialized();

        // Initialize networking receivers. Other components are registered via `AkytheraFabric`
        ClientBoundNetworkReceiverRegistrar.registerTypes();
        ClientBoundNetworkReceiverRegistrar.registerReceivers();

        // Register reload listeners
        ResourceLoader clientResourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        clientResourceLoader.registerReloadListener(Akythera.identifier("anim_manager_dynamic"), AnimationManager.getInstance());
        clientResourceLoader.registerReloadListener(Akythera.identifier("armature_manager_dynamic"), ArmatureManager.getInstance());
        clientResourceLoader.registerReloadListener(Akythera.identifier("mesh_manager_dynamic"), MeshManager.getInstance());
    }
}
