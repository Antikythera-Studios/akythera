package com.yesman.akythera.api.data.modularize.impl;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Comparator;

/// Built-in natural-order comparators for the numeric primitive script types, addressable by name so they
/// can be serialized as a script value (the comparators themselves are stateless singletons).
public final class NumericComparators {
    private NumericComparators() {}

    private static final BiMap<String, Comparator<?>> BY_NAME = HashBiMap.create();

    public static final Comparator<Byte>    BYTE_COMPARATOR    = register("byte",    Byte::compare);
    public static final Comparator<Short>   SHORT_COMPARATOR   = register("short",   Short::compare);
    public static final Comparator<Integer> INTEGER_COMPARATOR = register("integer", Integer::compare);
    public static final Comparator<Long>    LONG_COMPARATOR    = register("long",    Long::compare);
    public static final Comparator<Float>   FLOAT_COMPARATOR   = register("float",   Float::compare);
    public static final Comparator<Double>  DOUBLE_COMPARATOR  = register("double",  Double::compare);

    /// A type-agnostic comparator over any boxed [Number]
    public static final Comparator<Number>  NUMBER_COMPARATOR  = register("number", Comparator.comparingDouble(Number::doubleValue));

    public static final Codec<Comparator<?>> CODEC = Codec.STRING.dispatch(
        "comparator",
        comparator -> {
            String name = BY_NAME.inverse().get(comparator);
            if (name == null) throw new IllegalArgumentException(comparator + " is not a built-in numeric comparator");

            return name;
        },
        name -> {
            Comparator<?> comparator = BY_NAME.get(name);
            if (comparator == null) throw new IllegalArgumentException(name + " is not a built-in numeric comparator");

            return MapCodec.unit(comparator);
        }
    );

    private static <T> Comparator<T> register(final String name, final Comparator<T> comparator) {
        BY_NAME.put(name, comparator);
        return comparator;
    }
}