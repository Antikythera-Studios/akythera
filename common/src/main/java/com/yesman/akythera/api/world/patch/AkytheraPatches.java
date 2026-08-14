package com.yesman.akythera.api.world.patch;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// A util class that helps extract **patches**, the epic fight dedicated modules for in-game
/// objects like entities and items
@NullMarked
public final class AkytheraPatches {
    private AkytheraPatches() {}

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(AkytheraPatches::clearCachedItemPatches);
    }

    /// Cached [ItemPatch]s after [StaticRegistryAccess#getRegistryAccess] is loaded
    ///
    /// Since item patches can't be built on data pack load due to its parent-child hierarchy
    /// the patch is initially assembled in first reference
    private static final Map<Item, @Nullable ItemPatch> ITEM_PATCH_CACHE = new HashMap<>();

    /// Unload registry access when quitting a world
    private static void clearCachedItemPatches(RegistryAccessEvent.Unload event) {
        ITEM_PATCH_CACHE.clear();
    }

    public static @Nullable ItemPatch getItemPatch(ItemStack itemStack) {
        Item item = itemStack.getItem();

        if (!ITEM_PATCH_CACHE.containsKey(item)) {
            ResourceKey<ItemPatchBlueprint> itemPatchResourceKey =
                ResourceKey.create(
                    AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
                    Identifier.parse(item.builtInRegistryHolder().getRegisteredName())
                );

            ITEM_PATCH_CACHE.put(
                item,
                StaticRegistryAccess.getRegistryAccess().get(itemPatchResourceKey)
                    .map(blueprint -> blueprint.value().instantiate(item))
                    .orElse(null)
            );
        }

        return ITEM_PATCH_CACHE.get(item);
    }

    /// This method should remain as the secondary option, especially when you can't fix local variables inside lambda expression.
    ///
    /// @param entity An entity object to extract an entity patch
    @SuppressWarnings("unchecked")
    public static <E extends Entity, T extends EntityPatch<E>> @Nullable T getEntityPatch(@Nullable Entity entity) {
        if (entity != null) {
            EntityPatch<?> entityPatchUncasted = ((EntityAccessor)entity).akythera$getEntityPatch();

            if (entityPatchUncasted != null) {
                try {
                    return (T)entityPatchUncasted;
                } catch(ClassCastException e) {
                    // If casting failed, the patch is not a compatible type
                    return null;
                }
            }
        }

        return null;
    }

    /// Returns an [Optional] instance that holds the execution result of [#getEntityPatch]
    public static <E extends Entity, T extends EntityPatch<E>> Optional<T> getEntityPatchOptional(@Nullable Entity entity) {
        return Optional.ofNullable(AkytheraPatches.<E, T>getEntityPatch(entity));
    }

    @ClientOnly
    public static class Clients {
        /// Returns [EntityPatch] from cached player in [Minecraft]
        /// Warning: developers must check physical & logical side before calling this method
        public static @Nullable EntityPatch<LocalPlayer> getCachedLocalPlayerPatch() {
            if (Minecraft.getInstance().player == null) {
                return null;
            }

            return getEntityPatch(Minecraft.getInstance().player);
        }

        /// Returns [EntityPatch] as an optional object from cached player in [Minecraft]
        /// Warning: developers must check physical & logical side before calling this method
        public static Optional<EntityPatch<LocalPlayer>> getCachedLocalPlayerPatchOptional() {
            return Optional.ofNullable(getCachedLocalPlayerPatch());
        }
    }
}
