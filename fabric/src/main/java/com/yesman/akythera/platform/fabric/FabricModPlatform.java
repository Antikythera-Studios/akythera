package com.yesman.akythera.platform.fabric;

import com.mojang.serialization.Codec;
import com.yesman.akythera.platform.ModPlatform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@NullMarked
public final class FabricModPlatform implements ModPlatform {
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(final String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public boolean triggerMixinHook() {
        return true;
    }

    @Override
    public @Nullable ModInfo getLoadedModInfo(final String id) {
        if (!isModLoaded(id)) {
            return null;
        }

        ModContainer modContainer = FabricLoader.getInstance().getModContainer(id).orElseThrow();

        return new ModInfo(
            id,
            modContainer.getMetadata().getName(),
            modContainer.getMetadata().getVersion().getFriendlyString(),
            modContainer.getOrigin().getPaths().getFirst().toFile()
        );
    }

    @Override
    public Collection<ModInfo> getLoadedMods() {
        return FabricLoader.getInstance().getAllMods().stream().map(modContainer ->
            new ModInfo(
                modContainer.getMetadata().getId(),
                modContainer.getMetadata().getName(),
                modContainer.getMetadata().getVersion().getFriendlyString(),
                modContainer.getOrigin().getPaths().getFirst().toFile()
            )
        ).toList();
    }

    @Override
    public List<PackResources> openModPacks() {
        List<PackResources> packs = new ArrayList<>();

        for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
            PackLocationInfo location = new PackLocationInfo(
                "mod/" + modContainer.getMetadata().getId(), Component.literal(modContainer.getMetadata().getName()), PackSource.BUILT_IN, Optional.empty()
            );

            for (Path root : modContainer.getRootPaths()) {
                packs.add(new PathPackResources(location, root));
            }
        }

        return packs;
    }

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> id) {
        return FabricRegistryBuilder.create(id).buildAndRegister();
    }

    @Override
    public <T> void createDataPackRegistry(ResourceKey<Registry<T>> id, Codec<T> codec) {
        DynamicRegistries.registerSynced(id, codec);
    }

    @Override
    public <T> Registrar<T> getRegistrar(ResourceKey<? extends Registry<T>> id, String namespace) {
        return new VanillaRegistrar<>(id, namespace);
    }

    @Override
    public void registerReloadListener(Identifier id, PackType packType, PreparableReloadListener reloadListener) {
        ResourceLoader.get(packType).registerReloadListener(id, reloadListener);
    }

    @Override
    public void registerDataPackSyncCallback(BiConsumer<ServerPlayer, Boolean> callback) {
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(callback::accept);
    }

    @Override
    public void registerConfigurationPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, _) -> payloads.get().forEach(payload -> {
            if (ServerConfigurationNetworking.canSend(handler, payload.type())) {
                ServerConfigurationNetworking.getSender(handler).sendPacket(payload);
            }
        }));
    }

    @Override
    public PhysicalSide getPhysicalSide() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? PhysicalSide.CLIENT : PhysicalSide.SERVER;
    }

    private record VanillaRegistrar<T>(Registry<T> registry, String namespace) implements Registrar<T> {
        @SuppressWarnings("unchecked")
        public VanillaRegistrar(ResourceKey<? extends Registry<T>> id, String namespace) {
            this((Registry<T>)BuiltInRegistries.REGISTRY.getValue(id.identifier()), namespace);
        }

        @Override
        public <O extends T> Holder<O> register(String name, Supplier<O> supplier) {
            return Registry.registerForHolder(registry, Identifier.fromNamespaceAndPath(namespace, name), supplier.get());
        }

        @Override
        public <O extends T> Holder<O> register(String name, Function<Identifier, O> registryFunction) {
            Identifier key = Identifier.fromNamespaceAndPath(namespace, name);
            return Registry.registerForHolder(registry, key, registryFunction.apply(key));
        }
    }
}
