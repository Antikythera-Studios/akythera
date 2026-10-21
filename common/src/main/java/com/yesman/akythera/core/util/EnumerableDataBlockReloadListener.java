package com.yesman.akythera.core.util;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.TreeMap;

/// Loads data-pack-defined [EnumerableDataBlock]s from
/// `data/<ns>/akythera/<datablock dir>/*.json` on server start and every /reload,
/// ingesting them into the bound [EnumerableDataBlockManager] after the code-side
/// (built-in) section.
///
/// The last loaded snapshot is kept for client synchronization (see
/// EnumerableDataBlockSync); entries are identifier-sorted so client and server
/// assign identical universal ordinals.
@NullMarked
public final class EnumerableDataBlockReloadListener<T extends EnumerableDataBlock> extends SimpleJsonResourceReloadListener<T> {
    private final EnumerableDataBlockManager<T> enumManager;

    /// Sorted snapshot of the last reload, the payload source for joining/reloaded clients
    private Map<Identifier, T> lastLoadedEntries = Map.of();
    private final Codec<T> elementCodec;

    public EnumerableDataBlockReloadListener(EnumerableDataBlockManager<T> enumManager) {
        super(enumManager.getMapCodec().codec(), FileToIdConverter.registry(enumManager.directoryKey()));

        this.enumManager = enumManager;
        this.elementCodec = enumManager.getMapCodec().codec();
    }

    @Override
    protected void apply(Map<Identifier, T> parsed, ResourceManager resourceManager, ProfilerFiller profiler) {
        lastLoadedEntries = new TreeMap<>(enumManager.canonicalizeDataPackEntries(parsed));
        enumManager.loadDataPackEntries(lastLoadedEntries);

        // The ingested count, which a path-identified type grows with the ancestors it generated.
        Akythera.LOGGER.debug(
            "Loaded {} data pack {} entries: {}",
            lastLoadedEntries.size(), enumManager.getEnumType(), lastLoadedEntries.keySet()
        );
    }

    public void loadNow(final ResourceManager resourceManager) {
        apply(prepare(resourceManager, InactiveProfiler.INSTANCE), resourceManager, InactiveProfiler.INSTANCE);
    }

    public Map<Identifier, T> getLastLoadedEntries() {
        return lastLoadedEntries;
    }

    public EnumerableDataBlockManager<T> getEnumManager() {
        return enumManager;
    }

    public Codec<T> getElementCodec() {
        return elementCodec;
    }
}