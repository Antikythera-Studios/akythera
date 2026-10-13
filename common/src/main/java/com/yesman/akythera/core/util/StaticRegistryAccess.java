package com.yesman.akythera.core.util;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// [RegistryAccess] manager to ease accessing the currently loaded
/// dynamic registry (data pack) by intercepting level login/disconnect
/// times.
public final class StaticRegistryAccess {
    /// Registry access for the current world
    @Nullable
    private static RegistryAccess registryAccess;

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_LOAD.registerEvent(StaticRegistryAccess::onRegistryAccessLoaded);
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(StaticRegistryAccess::onRegistryAccessUnloaded);
    }

    /// Load registry access when joining a world
    private static void onRegistryAccessLoaded(RegistryAccessEvent.Load event) {
        registryAccess = event.getRegistryAccess();
    }

    /// Unload registry access when quitting a world
    private static void onRegistryAccessUnloaded(RegistryAccessEvent.Unload event) {
        registryAccess = null;
    }

    /// Returns a currently loaded registry access. Must have any loaded registry access. In other words there must be
    /// a world that player joined.
    public static @NonNull RegistryAccess getRegistryAccess() throws IllegalStateException {
        if (registryAccess == null) {
            throw new IllegalStateException("Tried to access registries before loaded. You must join a world.");
        }

        return registryAccess;
    }

    /// Returns a currently loaded registry access. Doesn't check the null so use it at your own risk.
    public static @Nullable RegistryAccess getRegistryAccessUnsafe() {
        return registryAccess;
    }

    /// Debug Only: dereference the [Identifier] from currently loaded registry access.
    /// Avoid calling [#toString] of this item in the product side.
    public static <T> String getRegisteredName(ResourceKey<@NonNull Registry<@NonNull T>> registryKey, T object) {
        if (getRegistryAccessUnsafe() == null) {
            return "Dangling " + object.getClass().getSimpleName();
        }

        // Doesn't handle exception from 'lookupOrThrow' here. If it throws an error the whole mod system is broken.
        Identifier identifier = getRegistryAccess().lookupOrThrow(registryKey).getKey(object);

        if (identifier == null) {
            return "Dangling " + object.getClass().getSimpleName();
        }

        return identifier.toString();
    }


    private StaticRegistryAccess() {}
}
