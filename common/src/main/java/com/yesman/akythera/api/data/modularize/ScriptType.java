package com.yesman.akythera.api.data.modularize;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
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
/// @param implicitlyCastableTypes Types that support implicit type-casting. (e.g. double -> float) Explicit casts
///                                (e.g. float -> double) are not included here. Didn't select `ScriptType` as a key
///                                since **use before declaration** issue. Not a null. Provide as least an empty map.
/// @param usedAsVariable Indicates if the script type can be used as variable in script editor
/// @param streamCodec Null when a variable of this type cannot cross the wire, which forces every
///                    variable declaring it to a sync direction of none
public record ScriptType(
    Class<?> type,
    @Nullable Codec<?> codec,
    @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes,
    boolean usedAsVariable,
    @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ?> streamCodec
) {
    // Expose the type only to engine. Won't be used in AkytheraLabs
    public ScriptType(Class<?> type) {
        this(type, null, Map.of(), false, null);
    }

    // Expose the type to both in engine and editor. Used for script variables that doesn't support explicit type conversion.
    public ScriptType(Class<?> type, @Nullable Codec<?> codec) {
        this(type, codec, Map.of(), true, null);
    }

    /// A variable type that crosses the wire.
    public ScriptType(
        Class<?> type,
        @Nullable Codec<?> codec,
        @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ?> streamCodec
    ) {
        this(type, codec, Map.of(), true, streamCodec);
    }

    // Expose the type to both in engine and editor. Used for script variables
    public ScriptType(Class<?> type, @Nullable Codec<?> codec, @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes) {
        this(type, codec, implicitlyCastableTypes, true, null);
    }

    public ScriptType(
        Class<?> type,
        @Nullable Codec<?> codec,
        @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes,
        boolean usedAsVariable
    ) {
        this(type, codec, implicitlyCastableTypes, usedAsVariable, null);
    }

    /// Whether a variable of this type may be synced at all.
    public boolean isSyncable() {
        return streamCodec != null;
    }

    /// The registered [ScriptType] for `type` (boxing-aware), or null when it isn't registered.
    public static @Nullable ScriptType of(Class<?> type) {
        Class<?> boxed = ParseUtils.box(type);

        for (ScriptType scriptType : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (ParseUtils.box(scriptType.type()) == boxed) return scriptType;
        }

        return null;
    }

    /// Whether a value of `from` fills a `to` slot: directly assignable, or through the implicit cast
    /// `from`'s type registers for `to` (e.g. `PlayerPatch -> Player`).
    public static boolean castable(Class<?> from, Class<?> to) {
        if (ParseUtils.box(to).isAssignableFrom(ParseUtils.box(from))) return true;

        ScriptType scriptType = of(from);

        return scriptType != null && scriptType.implicitlyCastableTypes().containsKey(ParseUtils.box(to));
    }

    /// Compare if the given class type can be used as parameter of `index`
    public boolean compatible(ScriptType other) {
        return type.isAssignableFrom(other.type());
    }
}
