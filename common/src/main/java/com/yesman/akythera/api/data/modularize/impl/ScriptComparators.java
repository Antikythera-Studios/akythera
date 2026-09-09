package com.yesman.akythera.api.data.modularize.impl;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Comparator;
import java.util.Objects;

/// Built-in comparators the script comparison functions run on, addressable by name so they can be
/// serialized as a script value (the comparators themselves are stateless singletons).
public final class ScriptComparators {
    private ScriptComparators() {}

    private static final BiMap<String, Comparator<?>> BY_NAME = HashBiMap.create();

    public static final Comparator<Byte>    BYTE_COMPARATOR    = register("byte",    Byte::compare);
    public static final Comparator<Short>   SHORT_COMPARATOR   = register("short",   Short::compare);
    public static final Comparator<Integer> INTEGER_COMPARATOR = register("integer", Integer::compare);
    public static final Comparator<Long>    LONG_COMPARATOR    = register("long",    Long::compare);
    public static final Comparator<Float>   FLOAT_COMPARATOR   = register("float",   Float::compare);
    public static final Comparator<Double>  DOUBLE_COMPARATOR  = register("double",  Double::compare);

    /// A type-agnostic comparator over any boxed [Number]
    public static final Comparator<Number>  NUMBER_COMPARATOR  = register("number", Comparator.comparingDouble(Number::doubleValue));

    /// Equality over any two values, in [Comparator] shape so
    /// [com.yesman.akythera.core.util.helper.MathUtils#equal] can run on it: `0` when the two are
    /// equal and `1` when they are not.
    ///
    /// Two [Number]s compare by their double value, so `1` and `1.0F` are equal across the boxed
    /// types the script canvas mixes freely. Everything else goes through [Objects#equals], which is
    /// identity for enums and for the [com.yesman.akythera.core.util.EnumerableDataBlock] singletons
    /// a data block manager hands out.
    ///
    /// Ordering is **not** defined: only the equality functions may run on this comparator.
    public static final Comparator<Object> EQUALITY_COMPARATOR = register("equality", (a, b) -> {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue());
        }

        return Objects.equals(a, b) ? 0 : 1;
    });

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