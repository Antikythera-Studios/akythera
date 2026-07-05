package com.yesman.akythera.api.data.modularize;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/// Serializable class type
///
/// Identifier compressed as "namespace:simple_class_name". For java
/// primitive functions, it has the default namespace, "minecraft"
///
/// @param codec Null when the type doesn't support serialization.
///              All types with the null codec won't support the
///              default value assignment in script editor.
/// @param explicitCastableTypes Types that support explicit type-casting (e.g. double -> float) implicit castable
///                              types (e.g. float -> double) are not included here. Didn't use `ScriptType` as key
///                              since "use before declaration" issue. Can't be null, provide as least an empty map.
/// @param usedAsVariable Indicates if the script type can be used as variable in script editor
public record ScriptType(Class<?> type, @Nullable Codec<?> codec, @NonNull Map<Class<?>, Function<?, ?>> explicitCastableTypes, boolean usedAsVariable) {
    // Expose the type only to engine. Won't be used in AkytheraLabs
    public ScriptType(Class<?> type) {
        this(type, null, Map.of(), false);
    }

    // Expose the type to both in engine and editor. Used for script variables that doesn't support explicit type conversion.
    public ScriptType(Class<?> type, @Nullable Codec<?> codec) {
        this(type, codec, Map.of(), true);
    }

    // Expose the type to both in engine and editor. Used for script variables
    public ScriptType(Class<?> type, @Nullable Codec<?> codec, @NonNull Map<Class<?>, Function<?, ?>> explicitCastableTypes) {
        this(type, codec, explicitCastableTypes, true);
    }

    /// Compare if the given class type can be used as parameter of `index`
    public boolean compatible(ScriptType other) {
        return type.isAssignableFrom(other.type());
    }
}
