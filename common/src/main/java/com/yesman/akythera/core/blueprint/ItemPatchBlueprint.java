package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/// Datapack blueprint that assembles [ItemPatch]es
///
/// Unlike [EntityPatchBlueprint], item patch blueprints form a parent-child hierarchy:
/// [#parent] points at another registry entry, and components are collected from the root
/// ancestor down to this blueprint — the closest declaration wins per component type.
public record ItemPatchBlueprint(
    Optional<ResourceKey<ItemPatchBlueprint>> parent,
    Map<String, ComponentBlueprint<?, ?>> components
) implements Blueprint<ItemPatch, Item>
{
    public static final MapCodec<ItemPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
                    .optionalFieldOf("parent").forGetter(ItemPatchBlueprint::parent),
                Codec.unboundedMap(
                    Codec.STRING,
                    AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.byNameCodec()
                        .<ComponentBlueprint<?, ?>>dispatch(ComponentBlueprint::mapCodec, Function.identity())
                ).fieldOf("components").forGetter(ItemPatchBlueprint::components)
            ).apply(instance, ItemPatchBlueprint::new)
    );

    public static final Codec<ItemPatchBlueprint> CODEC = MAP_CODEC.codec();

    /// Builds an [ItemPatch] after resolving the parent chain. Requires a loaded registry access.
    @Override
    public ItemPatch instantiate(final Item blueprintOwner) {
        Map<String, PatchComponent> componentsToAdd = new HashMap<>();

        for (ItemPatchBlueprint blueprint : hierarchy()) {
            for (Map.Entry<String, ComponentBlueprint<?, ?>> entry : blueprint.components.entrySet()) {
                componentsToAdd.put(entry.getKey(), entry.getValue().instantiate(null));
            }
        }

        return new ItemPatch(blueprintOwner, componentsToAdd);
    }

    /// Parent chain ordered root → this, so nearer blueprints win on component collision
    private List<ItemPatchBlueprint> hierarchy() {
        List<ItemPatchBlueprint> chain = new ArrayList<>();
        Set<ResourceKey<ItemPatchBlueprint>> visited = new HashSet<>();
        ItemPatchBlueprint current = this;
        chain.add(current);

        while (current.parent.isPresent()) {
            ResourceKey<ItemPatchBlueprint> parentKey = current.parent.get();

            if (!visited.add(parentKey)) {
                throw new IllegalStateException("Circular item patch hierarchy: " + parentKey);
            }

            current = StaticRegistryAccess.getRegistryAccess().getOrThrow(parentKey).value();
            chain.add(current);
        }

        return chain.reversed();
    }

    @Override
    public MapCodec<ItemPatchBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
