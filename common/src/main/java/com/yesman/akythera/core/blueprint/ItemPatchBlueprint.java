package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

/// Datapack blueprint that assembles [ItemPatch]es
///
/// Item patch blueprints form a parent-child hierarchy: [#parent] points at another registry entry,
/// and components are collected from the root ancestor down to this blueprint.
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
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ITEM_PATCH_COMPONENT_TYPES)
                    .fieldOf("components").forGetter(ItemPatchBlueprint::components)
            ).apply(instance, ItemPatchBlueprint::new)
    );

    public static final Codec<ItemPatchBlueprint> CODEC = MAP_CODEC.codec();

    /// Builds the shared [ItemPatch] after resolving the parent chain. Requires a loaded registry access.
    @Override
    public ItemPatch instantiate(final Item item) {
        return new ItemPatch(item, this);
    }

    /// The [TransformComponent]s of `shared`, the patch this blueprint built, each built again under `host` from the
    /// nearest blueprint of the parent chain declaring it. Skips the ones [ComponentBlueprint#buildsFor] refuses on
    /// `host`'s side. Requires a loaded registry access.
    @SuppressWarnings("unchecked")
    public Map<String, PatchComponent> instantiateTransformComponents(final ItemPatch shared, final ComponentAttachable host) {
        List<ItemPatchBlueprint> nearestFirst = hierarchy(this).reversed();
        boolean logicalClient = host.logicalSide().validInClientSide();
        Map<String, PatchComponent> built = new LinkedHashMap<>();

        shared.getComponents().forEach((name, component) -> {
            if (!(component instanceof TransformComponent)) return;

            nearestFirst.stream()
                .map(blueprint -> blueprint.components.get(name))
                .filter(Objects::nonNull)
                .findFirst()
                .filter(blueprint -> blueprint.buildsFor(logicalClient))
                .ifPresent(blueprint -> built.put(name, ((ComponentBlueprint<?, Object>)blueprint).instantiate(host)));
        });

        return built;
    }

    /// The whole parent chain's components instantiated into one map, root first and each blueprint
    /// laid over its ancestors by [PatchComponent#fold]. Every [WeaponStanceVariationAdapter] in the
    /// result carries the patch's other components under its own children, its own declarations
    /// winning.
    ///
    /// `itemPatch` is under construction — it is the owner every component blueprint is built
    /// against, and its own component map is not assigned until this returns.
    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public static Map<String, PatchComponent> assembleComponents(final ItemPatchBlueprint rootBlueprint, final ItemPatch itemPatch) {
        List<Map<String, PatchComponent>> layers = new ArrayList<>();

        for (ItemPatchBlueprint blueprint : hierarchy(rootBlueprint)) {
            Map<String, PatchComponent> layer = new LinkedHashMap<>();
            blueprint.components.forEach((name, component) -> layer.put(name, ((ComponentBlueprint<?, Object>)component).instantiate(itemPatch)));
            layers.add(layer);
        }

        Map<String, PatchComponent> components = PatchComponent.fold(layers);
        Map<String, PatchComponent> common = new LinkedHashMap<>();
        Map<String, WeaponStanceVariationAdapter> adapters = new LinkedHashMap<>();

        components.forEach((name, component) -> {
            if (component instanceof WeaponStanceVariationAdapter adapter) {
                adapters.put(name, adapter);
            } else {
                common.put(name, component);
            }
        });

        adapters.forEach((name, adapter) -> components.put(name, adapter.withInheritedComponents(common)));

        return components;
    }

    /// Parent chain ordered root → this, so nearer blueprints win on component collision
    private static List<ItemPatchBlueprint> hierarchy(final ItemPatchBlueprint blueprint) {
        List<ItemPatchBlueprint> chain = new ArrayList<>();
        Set<ResourceKey<ItemPatchBlueprint>> visited = new HashSet<>();
        ItemPatchBlueprint current = blueprint;
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
