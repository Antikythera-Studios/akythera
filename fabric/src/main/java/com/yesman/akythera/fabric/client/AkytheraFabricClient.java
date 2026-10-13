package com.yesman.akythera.fabric.client;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.asset.physics.ArmaturePhysicsManager;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.AkytheraClient;
import com.yesman.akythera.client.input.AkytheraKeyMappings;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.fabric.client.networking.ClientBoundNetworkReceiverRegistrar;
import com.yesman.akythera.fabric.networking.ClientBoundPayloadTypes;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.server.packs.PackType;
import net.neoforged.fml.config.ModConfig;

public final class AkytheraFabricClient implements ClientModInitializer {
    @SuppressWarnings("unchecked")
    public AkytheraFabricClient() {
        AkytheraClient.onModConstructed();

        ClientComponentsFactory.getImpl().setArmorSubmitter((poseStack, submitNodeCollector, stack, state, slot, lightCoords, parentModel) -> {
            ArmorRenderer armorRenderer = ArmorRendererRegistryImpl.get(stack.getItem());

            if (armorRenderer == null
                || !(state instanceof HumanoidRenderState humanoidState)
                || !(parentModel instanceof HumanoidModel<?> humanoidModel)
            ) {
                return false;
            }

            armorRenderer.render(
                poseStack, submitNodeCollector, stack, humanoidState, slot, lightCoords, (HumanoidModel<HumanoidRenderState>) humanoidModel
            );

            return true;
        });
    }

    @Override
    public void onInitializeClient() {
        AkytheraClient.onModInitialized();

        // Initialize networking receivers. Other components are registered via `AkytheraFabric`
        ClientBoundPayloadTypes.registerTypes();
        ClientBoundNetworkReceiverRegistrar.registerReceivers();

        AkytheraKeyMappings.all().forEach(KeyMappingHelper::registerKeyMapping);

        // Register reload listeners
        ResourceLoader clientResourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        clientResourceLoader.registerReloadListener(Akythera.identifier("anim_manager_dynamic"), AnimationManager.getInstance());
        clientResourceLoader.registerReloadListener(Akythera.identifier("armature_manager_dynamic"), ArmatureManager.getInstance());
        clientResourceLoader.registerReloadListener(Akythera.identifier("mesh_manager_dynamic"), MeshManager.getInstance());
        clientResourceLoader.registerReloadListener(Akythera.identifier("armature_physics_manager_dynamic"), ArmaturePhysicsManager.getInstance());

        ModConfigEvents.loading(Akythera.MODID).register(modConfig -> {
            if (modConfig.getType() == ModConfig.Type.CLIENT) {
                AkytheraClientConfig.initConfigValues();
            }
        });

        ModConfigEvents.reloading(Akythera.MODID).register(modConfig -> {
            if (modConfig.getType() == ModConfig.Type.CLIENT) {
                AkytheraClientConfig.initConfigValues();
            }
        });

        ConfigRegistry.INSTANCE.register(Akythera.MODID, ModConfig.Type.CLIENT, AkytheraClientConfig.SPEC);
    }
}
