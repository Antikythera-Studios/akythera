package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Registry;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ComponentBlueprint<T extends PatchComponent, O> extends Blueprint<T, O> {
    /// A host's children: the name each one is attached under
    /// ([ComponentAttachable#getComponents]) mapped to its blueprint, dispatched on the component
    /// type id.
    ///
    /// `types` is the patch kind's component registry —
    /// [AkytheraRegistries.Frozen#ENTITY_PATCH_COMPONENT_TYPES] or
    /// [AkytheraRegistries.Frozen#ITEM_PATCH_COMPONENT_TYPES]. It arrives as a supplier and the codec
    /// is lazily initialized: a host's own codec is an entry of the registry it dispatches through.
    static Codec<Map<String, ComponentBlueprint<?, ?>>> componentMapCodec(
        Supplier<Registry<MapCodec<? extends ComponentBlueprint<?, ?>>>> types
    ) {
        return Codec.lazyInitialized(() ->
            Codec.unboundedMap(
                Codec.STRING,
                types.get().byNameCodec().dispatch(ComponentBlueprint::mapCodec, Function.identity())
            )
        );
    }

    @Override
    MapCodec<? extends ComponentBlueprint<T, O>> mapCodec();
}
