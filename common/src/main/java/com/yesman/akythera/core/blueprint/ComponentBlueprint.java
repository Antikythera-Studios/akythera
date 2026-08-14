package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.tags.TagKey;

import java.util.Map;
import java.util.function.Function;

public interface ComponentBlueprint<T extends PatchComponent, O> extends Blueprint<T, O> {
    TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_PATCH_COMPONENT_TAG =
        TagKey.create(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.identifier("entity_patch"));

    TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_PATCH_COMPONENT_TAG =
        TagKey.create(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.identifier("item_patch"));

    /// A host's children: the name each one is attached under
    /// ([ComponentAttachable#getComponents]) mapped to its blueprint, dispatched on the component
    /// type id.
    ///
    /// Lazily initialized — a host's own codec is an entry of the registry it dispatches through.
    static Codec<Map<String, ComponentBlueprint<?, ?>>> componentMapCodec() {
        return Codec.lazyInitialized(() ->
            Codec.unboundedMap(
                Codec.STRING,
                AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.byNameCodec()
                    .<ComponentBlueprint<?, ?>>dispatch(ComponentBlueprint::mapCodec, Function.identity())
            )
        );
    }

    @Override
    MapCodec<? extends ComponentBlueprint<T, O>> mapCodec();
}
