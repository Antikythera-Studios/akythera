package com.yesman.akythera.core.asset;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Reads assets on a dedicated server, where the game mounts the data tree only.
///
/// Assets ship at `data/<namespace>/assets/<path>`, so an id is rewritten into that subdirectory on
/// the way in and rewritten back on the way out of a listing. Callers keep addressing assets as
/// `<namespace>:<path>`.
///
/// The delegate is the server's own data pack resource manager, captured on each data pack reload.
@NullMarked
public final class DedicatedServerAssetResourceManager implements ResourceManager, PreparableReloadListener {
    /// Data tree subdirectory the assets are packed in.
    private static final String ASSET_DIRECTORY = "assets/";

    /// The server's data pack resource manager. Null until the first data pack reload.
    /// Written on the reload thread, read on the server thread.
    private volatile @Nullable ResourceManager dataPackResourceManager;

    /// `<namespace>:<path>` -> `<namespace>:assets/<path>`
    private static Identifier toDataPackPath(final Identifier assetPath) {
        return Identifier.fromNamespaceAndPath(assetPath.getNamespace(), ASSET_DIRECTORY + assetPath.getPath());
    }

    /// Inverse of [#toDataPackPath]. An id outside the assets subdirectory is returned as it is.
    private static Identifier toAssetPath(final Identifier dataPackPath) {
        String path = dataPackPath.getPath();

        if (!path.startsWith(ASSET_DIRECTORY)) {
            return dataPackPath;
        }

        return Identifier.fromNamespaceAndPath(dataPackPath.getNamespace(), path.substring(ASSET_DIRECTORY.length()));
    }

    /// Re-keys a listing from data pack paths back to asset paths.
    private static <T> Map<Identifier, T> toAssetPaths(final Map<Identifier, T> listed) {
        Map<Identifier, T> assets = new TreeMap<>();
        listed.forEach((id, value) -> assets.put(toAssetPath(id), value));

        return assets;
    }

    /// Empty until a data pack reload happened, so a lookup before the server is up misses
    /// instead of throwing.
    private ResourceManager delegate() {
        ResourceManager resourceManager = dataPackResourceManager;

        return resourceManager == null ? ResourceManager.Empty.INSTANCE : resourceManager;
    }

    /// ***************************************************************
    /// [PreparableReloadListener]
    /// ***************************************************************

    @Override
    public CompletableFuture<Void> reload(
        final PreparableReloadListener.SharedState currentReload,
        final Executor taskExecutor,
        final PreparableReloadListener.PreparationBarrier preparationBarrier,
        final Executor reloadExecutor
    ) {
        return CompletableFuture
            .runAsync(() -> dataPackResourceManager = currentReload.resourceManager(), taskExecutor)
            .thenCompose(preparationBarrier::wait);
    }

    /// ***************************************************************
    /// [ResourceManager]
    /// ***************************************************************

    @Override
    public Set<String> getNamespaces() {
        return delegate().getNamespaces();
    }

    @Override
    public Optional<Resource> getResource(final Identifier identifier) {
        return delegate().getResource(toDataPackPath(identifier));
    }

    @Override
    public List<Resource> getResourceStack(final Identifier identifier) {
        return delegate().getResourceStack(toDataPackPath(identifier));
    }

    @Override
    public Map<Identifier, Resource> listResources(final String path, final Predicate<Identifier> predicate) {
        return toAssetPaths(delegate().listResources(ASSET_DIRECTORY + path, id -> predicate.test(toAssetPath(id))));
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(final String path, final Predicate<Identifier> predicate) {
        return toAssetPaths(delegate().listResourceStacks(ASSET_DIRECTORY + path, id -> predicate.test(toAssetPath(id))));
    }

    @Override
    public Stream<PackResources> listPacks() {
        return delegate().listPacks();
    }
}