package com.yesman.akythera.core.util;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundDataBlockSync;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EnumerableDataBlockSync {
    private EnumerableDataBlockSync() {}

    private static final Map<Identifier, EnumerableDataBlockReloadListener<?>> LISTENERS = new LinkedHashMap<>();

    /// Call in the mod constructor. Creates + registers the reload listener and ties the
    /// type into join//reload synchronization.
    public static <T extends EnumerableDataBlock> void addSyncedDataBlock(EnumerableDataBlockManager<T> enumManager) {
        EnumerableDataBlockReloadListener<T> listener = new EnumerableDataBlockReloadListener<>(enumManager);
        Identifier id = enumManager.directoryKey().identifier();
        LISTENERS.put(id, listener);
        ModPlatformProvider.get().registerReloadListener(id, PackType.SERVER_DATA, listener);
    }

    public static void registerEvents() {
        ModPlatformProvider.get().registerDataPackSyncCallback(EnumerableDataBlockSync::syncToPlayer);
        ModPlatformProvider.get().registerConfigurationPayloads(EnumerableDataBlockSync::syncPayloads);

        // Client leaves a world -> evict data pack entries
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(
            _ -> LISTENERS.values().forEach(listener -> listener.getEnumManager().clearDataPackEntries())
        );
    }

    public static Collection<? extends EnumerableDataBlockManager<?>> getEditableManagers() {
        return LISTENERS.values().stream().map(EnumerableDataBlockReloadListener::getEnumManager).toList();
    }

    public static void loadBeforeRegistries(final ResourceManager resourceManager) {
        LISTENERS.values().forEach(listener -> listener.loadNow(resourceManager));
    }

    private static List<CustomPacketPayload> syncPayloads() {
        return LISTENERS.entrySet().stream().map(entry -> encodePayload(entry.getKey(), entry.getValue())).toList();
    }

    private static void syncToPlayer(ServerPlayer player, boolean joined) {
        NetworkingHelper.PayloadBundleBuilder bundle = NetworkingHelper.PayloadBundleBuilder.create();
        syncPayloads().forEach(bundle::and);
        bundle.send((first, rest) -> NetworkingHelper.getInstance().sendToClient(player, first, rest));
    }

    private static <T extends EnumerableDataBlock> CustomPacketPayload encodePayload(Identifier id, EnumerableDataBlockReloadListener<T> listener) {
        Codec<T> codec = listener.getElementCodec();
        Map<Identifier, Tag> entries = new LinkedHashMap<>();

        listener.getLastLoadedEntries().forEach((entryId, value) ->
            codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> Akythera.LOGGER.error("Failed to encode data block {} of {}: {}", entryId, id, error))
                .ifPresent(tag -> entries.put(entryId, tag))
        );

        return new ClientBoundDataBlockSync(id, entries);
    }

    /// Client-side ingest of a received [ClientBoundDataBlockSync]. Decodes each entry with
    /// the bound element codec and loads it into the matching manager's data pack section.
    public static void ingest(Identifier dataBlockType, Map<Identifier, Tag> entries) {
        EnumerableDataBlockReloadListener<?> listener = LISTENERS.get(dataBlockType);

        if (listener == null) {
            Akythera.LOGGER.warn("Received data block sync for unknown type {}", dataBlockType);
            return;
        }

        ingestInto(listener, entries);
    }

    private static <T extends EnumerableDataBlock> void ingestInto(EnumerableDataBlockReloadListener<T> listener, Map<Identifier, Tag> entries) {
        Codec<T> codec = listener.getElementCodec();
        Map<Identifier, T> decoded = new TreeMap<>();

        entries.forEach((entryId, tag) ->
            codec.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> Akythera.LOGGER.error("Failed to decode data block {}: {}", entryId, error))
                .ifPresent(value -> decoded.put(entryId, value))
        );

        listener.getEnumManager().loadDataPackEntries(decoded);
    }
}
