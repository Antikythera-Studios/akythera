package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;

/// Data classes that hold information about how to instantiate a class
///
/// @param <T> what the blueprint instantiates
/// @param <O> owner of `<T>`
public interface Blueprint<T, O> {
    T instantiate(final O blueprintOwner);

    MapCodec<? extends Blueprint<T, O>> mapCodec();
}
