package com.yesman.akythera.api.asset;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/// An asset manager for heavy-weight binary assets, like meshes, armatures, and animationsToReplace.
///
/// Loading an asset is done on the first reference, not when the game starts.
///
/// Use [AssetAccessor] to get, and check usability of assets.
///
/// Reload is bound to [PackType#CLIENT_RESOURCES] on a physical client and to
/// [PackType#SERVER_DATA] on a dedicated server, where the assets ride in the data tree.
@NullMarked
public abstract class DynamicAssetManager<T> implements PreparableReloadListener {
    /// Nullable: a case where an exception was thrown while trying to read asset file
    protected final Map<Identifier, @Nullable T> loadedObjectsRegistry = new HashMap<>();

    /// Managed asset accessors that could be dereferenced to track available assets
    ///
    /// Not all accessors are available even tho they registered in this container.
    ///
    /// Made as [ConcurrentHashMap] because data pack loading is multithreaded
    protected final Map<Identifier, AssetAccessor<? extends T>> issuedAccessor = new ConcurrentHashMap<>();

    /// All managers are instantiated by singleton pattern
    protected DynamicAssetManager() {}

    /// On resource pack reload, it doesn't reload all the assets but just
    /// clear the registry to effectively reload at the next reference.
    public void onAssetReloaded() {
        loadedObjectsRegistry.clear();
    }

    /// Returns an asset accessor by id
    public @Nullable AssetAccessor<? extends T> getRegisteredAccessorById(final Identifier identifier) {
        return issuedAccessor.get(identifier);
    }

    /// Iterate all compile time-registered accessors
    public Stream<AssetAccessor<? extends T>> listRegisteredAccessors() {
        return issuedAccessor.values().stream();
    }

    // Simple work: clear all loaded objects
    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload, Executor taskExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor reloadExecutor) {
        return CompletableFuture.runAsync(loadedObjectsRegistry::clear).thenCompose(preparationBarrier::wait);
    }
}
