package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/// Data classes that hold information about how to instantiate a class
///
/// @param <T> what the blueprint instantiates
/// @param <O> owner of `<T>`
public interface Blueprint<T, O> {
    T instantiate(final O blueprintOwner);

    MapCodec<? extends Blueprint<T, O>> mapCodec();

    /// The graphs, property values and variables this blueprint authors. Empty for one that authors none.
    default BlueprintBody body() {
        return BlueprintBody.EMPTY;
    }

    /// The entry of this blueprint's own registry its [#body] is laid over, see [BlueprintBody#chain].
    default Optional<Identifier> dataParent() {
        return Optional.empty();
    }
}
