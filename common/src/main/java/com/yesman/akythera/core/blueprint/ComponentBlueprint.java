package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Registry;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ComponentBlueprint<T extends PatchComponent, O> extends Blueprint<T, O> {
    /// The field every blueprint's codec declares for [#enabled]. Left out of the file while true.
    MapCodec<Boolean> ENABLED_CODEC = Codec.BOOL.optionalFieldOf("enabled", true);

    /// The field every [TransformComponent]'s blueprint declares for its local transform. Left out of the file while
    /// it is the identity.
    MapCodec<Transform> TRANSFORM_CODEC = Transform.CODEC.optionalFieldOf("transform", Transform.IDENTITY);

    /// The field every [TransformComponent]'s blueprint declares for its [TransformInheritPolicy]. Left out of the
    /// file while it is [TransformInheritPolicy#LOC_ROT_SCA].
    MapCodec<TransformInheritPolicy> TRANSFORM_INHERIT_POLICY_CODEC =
        TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA);

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

    /// Builds every entry of `blueprints` against `host` and hands it to `out` under its name,
    /// skipping the ones [#buildsFor] refuses here.
    @SuppressWarnings("unchecked")
    static void instantiateAll(
        final Map<String, ComponentBlueprint<?, ?>> blueprints,
        final Object host,
        final boolean logicalClient,
        final BiConsumer<String, PatchComponent> out
    ) {
        blueprints.forEach((name, blueprint) -> {
            if (!blueprint.buildsFor(logicalClient)) return;

            out.accept(name, ((ComponentBlueprint<?, Object>)blueprint).instantiate(host));
        });
    }

    /// Whether the component [#instantiate] builds starts switched on, see [PatchComponent#isEnabled].
    boolean enabled();

    /// Whether this blueprint builds its component for a host on this side and in this installation.
    /// One that answers false is skipped: the component does not exist, and a lookup by name answers
    /// null.
    default boolean buildsFor(final boolean logicalClient) {
        return true;
    }

    @Override
    MapCodec<? extends ComponentBlueprint<T, O>> mapCodec();
}
