package com.yesman.akythera.core;

import com.mojang.brigadier.CommandDispatcher;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.lifecycle.ServerComponentsFactory;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.commands.AnimatorControlCommand;
import com.yesman.akythera.compat.IModCompatibility;
import com.yesman.akythera.compat.MinecraftMod;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akythera.core.animation.impl.AkytheraAnimationLayerSignature;
import com.yesman.akythera.core.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.core.animation.impl.EpicFightAnimationInterfaceKey;
import com.yesman.akythera.core.asset.DedicatedServerModFileResourceManager;
import com.yesman.akythera.core.blueprint.AkytheraComponentBlueprints;
import com.yesman.akythera.core.registry.entry.AkytheraAnimNotifyStateTypes;
import com.yesman.akythera.core.registry.entry.AkytheraAnimNotifyTypes;
import com.yesman.akythera.core.util.EnumerableDataBlockSync;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;

/// Common functionalities shared between the platform entry points.
///
/// Note: Multi loader architecture isn't meant to supported addons.
/// Addon developers must set up custom Gradle project or use multi-loader
/// APIs like Architectury.
public final class Akythera {
    private Akythera() {
    }

    public static final String MODID = "akythera";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /// Creates an identifier that points to an Akythera resource.
    public static @NonNull Identifier identifier(@NonNull final String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /// Initializes common functionalities.
    ///
    /// Provides the global [ModPlatform] instance given by the mod platform (e.g., NeoForge, Fabric)
    /// to common vanilla code for sharing.
    public static void initializePlatform(@NonNull final ModPlatform platform) {
        ModPlatformProvider.initialize(platform);
        ComponentsFactory.initialize(platform.getPhysicalSide().isClient() ? new ClientComponentsFactory() : new ServerComponentsFactory());
    }

    /// Called on each mod loader's mod entry point class's constructor
    public static void onModConstructed() {
        AnimationInterfaceKey.ENUM_MANAGER.registerEnumCls(MODID, EpicFightAnimationInterfaceKey.class);
        FSMAnimationState.ENUM_MANAGER.registerEnumCls(MODID, AkytheraAnimationState.class);
        AnimationLayerSignature.ENUM_MANAGER.registerEnumCls(MODID, AkytheraAnimationLayerSignature.class);

        try {
            // Init the registry holder classes explicitly to preload registries before "newRegistryEvent" and "newDatapackRegistryEvent" are called
            Class.forName("com.yesman.akythera.core.registry.AkytheraRegistries$DataPack");
            Class.forName("com.yesman.akythera.core.registry.AkytheraRegistries$Frozen");

            AkytheraAnimNotifyTypes.REGISTRAR.notifyPresence();
            AkytheraAnimNotifyStateTypes.REGISTRAR.notifyPresence();
            AkytheraComponentBlueprints.REGISTRAR.notifyPresence();
            AkytheraScriptFunctions.AKYTHERA_REGISTRAR.notifyPresence();
            AkytheraScriptFunctions.MINECRAFT_REGISTRAR.notifyPresence();
            AkytheraScriptTypes.AKYTHERA_REGISTRAR.notifyPresence();
            AkytheraScriptTypes.MINECRAFT_REGISTRAR.notifyPresence();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Can't find registry holder class", e);
        }

        // Register level load & unload events to cache registry access for the helper class
        StaticRegistryAccess.registerEvents();
        AkytheraPatches.registerEvents();
        EnumerableDataBlockSync.addSyncedDataBlock(FSMAnimationState.ENUM_MANAGER);
        EnumerableDataBlockSync.addSyncedDataBlock(AnimationLayerSignature.ENUM_MANAGER);
        EnumerableDataBlockSync.registerEvents();
    }

    /// Called on mod loader lifecycle when the mod initializes
    public static void onModInitialized() {
        AnimationInterfaceKey.ENUM_MANAGER.loadEnum();
        FSMAnimationState.ENUM_MANAGER.loadEnum();
        AnimationLayerSignature.ENUM_MANAGER.loadEnum();

        Arrays.stream(MinecraftMod.values())
            .filter(mod -> ModPlatformProvider.get().isModLoaded(mod.getModId()) && mod.getCompatibilityModule() != null)
            .map(mod -> {
                try {
                    return mod.getCompatibilityModule().getDeclaredConstructor().newInstance();
                } catch (
                    InstantiationException |
                    IllegalAccessException |
                    InvocationTargetException |
                    NoSuchMethodException e
                ) {
                    Akythera.LOGGER.error("Can't create an instance of compatibility module {}. Skipped.", mod.getModId(), e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(IModCompatibility::onInitialize);
    }

    /// Called on mod loader lifecycle when the mod initializes in a dedicated server
    public static void onModInitializedInDedicatedServer() {
        ComponentsFactory.getImpl().setResourceManagerSupplier(DedicatedServerModFileResourceManager::new);
    }

    /// Register in-game commands
    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        AnimatorControlCommand.register(dispatcher);
    }
}
