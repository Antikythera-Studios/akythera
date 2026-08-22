package com.yesman.akythera.core;

import com.mojang.brigadier.CommandDispatcher;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.lifecycle.ServerComponentsFactory;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.commands.AnimatorControlCommand;
import com.yesman.akythera.compat.IModPlugin;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationLayerSignature;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.core.blueprint.AkytheraComponentBlueprints;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.registry.entry.AkytheraAnimNotifyStateTypes;
import com.yesman.akythera.core.registry.entry.AkytheraAnimNotifyTypes;
import com.yesman.akythera.core.util.EnumerableDataBlockSync;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
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

    /// Called on each mod loader's mod entry point class's constructor
    public static void onModConstructed() {
        ComponentsFactory.initialize(ModPlatformProvider.get().getPhysicalSide().isClient() ? new ClientComponentsFactory() : new ServerComponentsFactory());

        try {
            // Init the registry holder classes explicitly to preload registries before "newRegistryEvent" and "newDatapackRegistryEvent" are called
            Class.forName("com.yesman.akythera.core.registry.AkytheraRegistries$Frozen");
            Class.forName("com.yesman.akythera.core.registry.AkytheraRegistries$DataPack");

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
        Arrays.stream(CompatibleMod.values())
            .filter(mod -> ModPlatformProvider.get().isModLoaded(mod.getModId()) && mod.getCompatibilityModule() != null)
            .map(mod -> {
                try {
                    return mod.getCompatibilityModule().getDeclaredConstructor().newInstance();
                } catch (
                    InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e
                ) {
                    Akythera.LOGGER.error("Can't create an instance of compatibility module {}. Skipped.", mod.getModId(), e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(IModPlugin::onInitialize);

        AnimationInterfaceKey.ENUM_MANAGER.loadEnum();
        FSMAnimationState.ENUM_MANAGER.loadEnum();
        AnimationLayerSignature.ENUM_MANAGER.loadEnum();
        ColliderType.ENUM_MANAGER.loadEnum();

        // Pair logics reference the collider type constants, so they bind after the enum loads.
        CollisionDetectionLogics.registerBuiltInLogics();
    }

    public static void registerBuiltInDataBlockClasses() {
        FSMAnimationState.ENUM_MANAGER.registerEnumCls(MODID, AkytheraAnimationState.class);
        AnimationLayerSignature.ENUM_MANAGER.registerEnumCls(MODID, AkytheraAnimationLayerSignature.class);
        ColliderType.ENUM_MANAGER.registerEnumCls(MODID, AkytheraColliderType.class);
    }

    /// Called on mod loader lifecycle when the mod initializes in a dedicated server
    public static void onModInitializedInDedicatedServer() {
        ModPlatform platform = ModPlatformProvider.get();

        platform.registerReloadListener(
            identifier("dedicated_server_assets"),
            PackType.SERVER_DATA,
            ServerComponentsFactory.getImpl().getResourceManager()
        );

        // Assets ride in the data tree on this side, so their caches drop on a data pack reload.
        platform.registerReloadListener(identifier("armature_manager_dynamic"), PackType.SERVER_DATA, ArmatureManager.getInstance());
        platform.registerReloadListener(identifier("anim_manager_dynamic"), PackType.SERVER_DATA, AnimationManager.getInstance());
    }

    /// Register in-game commands
    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        AnimatorControlCommand.register(dispatcher);
    }
}
