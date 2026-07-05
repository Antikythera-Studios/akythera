package com.yesman.akythera.core.asset;

import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// A resource manager that extracts resources from all loaded mod jar files in a dedicated server.
///
/// On the physical client the game's own [net.minecraft.client.resources.ClientPackSource] already
/// mounts every mod's assets folder so [net.minecraft.client.Minecraft#getResourceManager()] covers
/// them all.
///
/// On a dedicated server no such mechanism exists, so this class replicates it by opening
/// each mod jar as a [FilePackResources] pack and delegating to a [MultiPackResourceManager].
@NullMarked
public final class DedicatedServerModFileResourceManager implements ResourceManager, AutoCloseable {
    private final MultiPackResourceManager delegate;

    public DedicatedServerModFileResourceManager() {
        List<PackResources> packs = ModPlatformProvider.get()
            .getLoadedMods()
            .stream()
            .filter(modInfo -> modInfo.modFile().exists())
            .map(DedicatedServerModFileResourceManager::openPack)
            .flatMap(Optional::stream)
            .toList();

        delegate = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs);
    }

    private static Optional<PackResources> openPack(ModPlatform.ModInfo modInfo) {
        PackLocationInfo location = new PackLocationInfo(
            modInfo.modId(),
            Component.literal(modInfo.modId()),
            PackSource.BUILT_IN,
            Optional.empty()
        );

        try {
            FilePackResources.FileResourcesSupplier supplier = new FilePackResources.FileResourcesSupplier(modInfo.modFile());
            return Optional.of(supplier.openPrimary(location));
        } catch (Exception e) {
            // The file may be a directory (in dev environments) or otherwise unreadable as a zip —
            // skip it silently; PathPackResources would be needed for directory-based mod sources.
            return Optional.empty();
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return delegate.getNamespaces();
    }

    @Override
    public Optional<Resource> getResource(Identifier identifier) {
        return delegate.getResource(identifier);
    }

    @Override
    public List<Resource> getResourceStack(Identifier identifier) {
        return delegate.getResourceStack(identifier);
    }

    @Override
    public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> predicate) {
        return delegate.listResources(path, predicate);
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> predicate) {
        return delegate.listResourceStacks(path, predicate);
    }

    @Override
    public Stream<PackResources> listPacks() {
        return delegate.listPacks();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
