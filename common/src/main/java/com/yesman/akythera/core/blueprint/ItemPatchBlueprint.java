package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenItemPatchScript;
import com.yesman.akythera.core.blueprint.script.ItemPatchScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Datapack blueprint that assembles [ItemPatch]es
///
/// Item patch blueprints form a parent-child hierarchy: [#parent] points at another registry entry,
/// and components are collected from the root ancestor down to this blueprint.
///
/// @param script what runs the [ItemPatchScript] events this blueprint authors
public record ItemPatchBlueprint(
    Optional<ResourceKey<ItemPatchBlueprint>> parent,
    Map<String, ComponentBlueprint<?, ?>> components,
    ScriptFactory script
) implements Blueprint<ItemPatch, Item>
{
    public static final MapCodec<ItemPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
                    .optionalFieldOf("parent").forGetter(ItemPatchBlueprint::parent),
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ITEM_PATCH_COMPONENT_TYPES)
                    .fieldOf("components").forGetter(ItemPatchBlueprint::components),
                ScriptFactory.MAP_CODEC.forGetter(ItemPatchBlueprint::script)
            ).apply(instance, ItemPatchBlueprint::new)
    );

    public static final Codec<ItemPatchBlueprint> CODEC = MAP_CODEC.codec();

    /// A blueprint authoring no event.
    public ItemPatchBlueprint(final Optional<ResourceKey<ItemPatchBlueprint>> parent, final Map<String, ComponentBlueprint<?, ?>> components) {
        this(parent, components, new ByData(BlueprintBody.EMPTY));
    }

    /// What runs the events of an item patch: a Java class, or graphs written beside the blueprint's own fields.
    /// A `script` field picks the class.
    public sealed interface ScriptFactory permits ByClass, ByData {
        MapCodec<ScriptFactory> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .validate(type -> ItemPatchScript.runnable(type.type())
                            ? DataResult.success(type)
                            : DataResult.error(() -> type.type().getSimpleName() + " is not a concrete " + ItemPatchScript.class.getSimpleName()
                                + " with a public constructor taking " + ItemPatch.class.getSimpleName() + " and " + ItemPatchScript.class.getSimpleName()))
                        .optionalFieldOf("script").forGetter(factory -> factory instanceof ByClass byClass ? Optional.of(byClass.type()) : Optional.empty()),
                    BlueprintBody.MAP_CODEC.forGetter(factory -> factory instanceof ByData byData ? byData.body() : BlueprintBody.EMPTY)
                ).apply(instance, (type, body) -> type.<ScriptFactory>map(ByClass::new).orElseGet(() -> new ByData(body)))
        );

        /// The script of `itemPatch` for `blueprint`, the blueprint of its parent chain holding this factory, over
        /// `parent`, the script of the ancestors. Answers `parent` itself when it authors no event.
        @Nullable ItemPatchScript instantiate(final ItemPatchBlueprint blueprint, final ItemPatch itemPatch, final @Nullable ItemPatchScript parent);
    }

    /// An [ItemPatchScript] class [ItemPatchScript#runnable] accepts.
    public record ByClass(ScriptType type) implements ScriptFactory {
        @Override
        public ItemPatchScript instantiate(final ItemPatchBlueprint blueprint, final ItemPatch itemPatch, final @Nullable ItemPatchScript parent) {
            return ItemPatchScript.instantiate(type.type(), itemPatch, parent);
        }
    }

    /// Graphs run by a [DataDrivenItemPatchScript]. Requires a loaded registry access.
    public record ByData(BlueprintBody body) implements ScriptFactory {
        @Override
        @SuppressWarnings("unchecked")
        public @Nullable ItemPatchScript instantiate(final ItemPatchBlueprint blueprint, final ItemPatch itemPatch, final @Nullable ItemPatchScript parent) {
            if (body.methods().isEmpty()) return parent;

            Registry<ItemPatchBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ITEM_PATCH_KEY);

            return new DataDrivenItemPatchScript((Holder.Reference<ItemPatchBlueprint>)registry.wrapAsHolder(blueprint), body, itemPatch, parent);
        }
    }

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

    /// The script of `itemPatch`, the patch under construction this blueprint builds: the script of the nearest
    /// blueprint of the parent chain authoring an event, each one's parent being its nearest ancestor's. Null when
    /// none authors any. Requires a loaded registry access.
    @ApiStatus.Internal
    public @Nullable ItemPatchScript instantiateScript(final ItemPatch itemPatch) {
        ItemPatchScript script = null;

        for (ItemPatchBlueprint blueprint : hierarchy(this)) {
            script = blueprint.script.instantiate(blueprint, itemPatch, script);
        }

        return script;
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
