package com.yesman.akythera.core.util.helper;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.Akythera;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParseUtils {
    /// Convert a [JsonArray] into primitive float array
    ///
    /// @throws JsonSyntaxException when given array is not a float array
    public static float[] jsonArrayToPrimitiveFloat(final JsonArray array) throws JsonSyntaxException {
        try {
            FloatList result = new FloatArrayList();

            for (JsonElement je : array) {
                result.add(je.getAsFloat());
            }

            return result.toFloatArray();
        } catch (NumberFormatException e) {
            throw new JsonSyntaxException("Can't convert to a float array", e);
        }
    }

    /// Convert a [JsonArray] into primitive int array
    ///
    /// @throws JsonSyntaxException when given array is not a int array
    public static int[] jsonArrayToPrimitiveInt(final JsonArray array) throws JsonSyntaxException {
        try {
            IntList result = new IntArrayList();

            for (JsonElement je : array) {
                result.add(je.getAsInt());
            }

            return result.toIntArray();
        } catch (NumberFormatException e) {
            throw new JsonSyntaxException("Can't convert to a int array", e);
        }
    }

    /// Returns an enum object by enum name that is non-case sensitive
    public static <T extends Enum<T>> @Nullable T enumValueOfOrNull(final Class<T> enumCls, final String enumName) {
        try {
            return Enum.valueOf(enumCls, enumName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /// Pack the map entry into list with key-value pair
    public static <F, S> List<Pair<F, S>> mapEntriesToPair(final Map<F, S> map) {
        List<Pair<F, S>> list = new ArrayList<>();

        for (Map.Entry<F, S> entry : map.entrySet()) {
            list.add(Pair.of(entry.getKey(), entry.getValue()));
        }

        return list;
    }

    /// Returns a string with lower cases
    public static String toLowerCase(final String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /// Returns a string with lower cases. Accepts any type of object and translate by `toString`
    public static String toLowerCase(final Object s) {
        return s.toString().toLowerCase(Locale.ROOT);
    }

    /// Returns a string with upper cases
    public static String toUpperCase(final String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /// Returns a string with upper cases. Accepts any type of object and translate by `toString`
    public static String toUpperCase(final Object s) {
        return s.toString().toUpperCase(Locale.ROOT);
    }

    /// Transform a snake case string into camel
    public static String snakeToCamel(final String snake) {
        if (snake == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(snake.length());
        boolean upper = false;

        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }

        return out.toString();
    }

    /// Transform a camel case string into snake
    public static String camelToSnake(final String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .toLowerCase(Locale.ROOT);
    }

    /// Transform a camel case string into spaced pascal case, for display labels.
    /// e.g. `getRootComponent` -> "Get Root Component"; `lastPosition` -> "Last Position".
    public static String camelToSpacedPascal(final String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /// `"player_script"` / `"humanoid/walk_run"` → `"PlayerScript"` / `"HumanoidWalkRun"` — the
    /// PascalCase entry-name half of the generated `<EntryName>AnimatorScript` class name.
    public static String pascalCase(final String idPath) {
        StringBuilder out = new StringBuilder(idPath.length());
        boolean upper = true;

        for (char c : idPath.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }

        return out.toString();
    }

    /// Returns whether param 's' can be converted to 'T'.
    public static <T extends Number> boolean isParsable(final String s, final Function<String, T> parser) {
        try {
            parser.apply(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /// Parse to string given float
    ///
    /// Live due to [Float#toString] is ambiguous when using as lambda
    public static String floatingPointToString(final float f) {
        return Float.toString(f);
    }

    /// Returns 'a' if it's not null. Otherwise, returns 'b'.
    public static <T> T nvl(@Nullable final T a, @NonNull final T b) {
        return a == null ? b : a;
    }

    /// Optional wrapper for providers, used for [RecordCodecBuilder]
    public static <O, T> Function<O, Optional<T>> provideOptional(final Function<O, @Nullable T> valueProvider) {
        return obj -> Optional.ofNullable(valueProvider.apply(obj));
    }

    /// Underscore-separated identifier → spaced, title-cased display name:
    /// `PLAYER_ANIMATION_LIBRARY` becomes `Player Animation Library`.
    ///
    /// Every character but a word's first is lowercased, so the result reads the same whether the
    /// source was screaming snake case (an enum constant name) or plain snake case (a serialized name).
    public static String toDisplayName(final String name) {
        StringBuilder displayName = new StringBuilder(name.length());
        boolean wordStart = true;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (c == '_') {
                displayName.append(' ');
                wordStart = true;
                continue;
            }

            displayName.append(wordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
            wordStart = false;
        }

        return displayName.toString();
    }

    /// Trim extension from path
    public static String trimExtensions(final String path, final String... expectedExtensions) {
        int idx = path.lastIndexOf(".");

        if (idx < 0) {
            return path;
        }

        String extension = path.substring(idx + 1);

        if (!Set.of(expectedExtensions).contains(extension)) {
            Akythera.LOGGER.warn("May trimmed wrong file extension from {}: {} are the only expected file extensions!", path, List.of(expectedExtensions));
        }

        return path.substring(0, idx);
    }

    /// Return a wrapper class type of primitive if the type supports it.
    public static Class<?> box(final Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == boolean.class) return Boolean.class;
        if (t == byte.class)    return Byte.class;
        if (t == short.class)   return Short.class;
        if (t == char.class)    return Character.class;
        if (t == int.class)     return Integer.class;
        if (t == long.class)    return Long.class;
        if (t == float.class)   return Float.class;
        if (t == double.class)  return Double.class;
        return t;
    }

    /// Return a primitive type of wrapper class if the type supports it.
    public static Class<?> unbox(final Class<?> c) {
        if (c == Float.class)     return float.class;
        if (c == Double.class)    return double.class;
        if (c == Integer.class)   return int.class;
        if (c == Long.class)      return long.class;
        if (c == Short.class)     return short.class;
        if (c == Byte.class)      return byte.class;
        if (c == Boolean.class)   return boolean.class;
        if (c == Character.class) return char.class;
        return c;
    }

    /// The generic type `raw<arguments>`, e.g. `parameterized(ResourceKey.class, AnimationMontage.class)` for
    /// `ResourceKey<AnimationMontage>`. Equal to the type reflection reports for the same declaration.
    public static ParameterizedType parameterized(final Class<?> raw, final Type... arguments) {
        return new Parameterized(raw, List.of(arguments));
    }

    /// The class `type` erases to: itself for a class, its raw class for a parameterized type, its first
    /// bound for a type variable.
    ///
    /// @throws IllegalArgumentException for a wildcard or a generic array
    public static Class<?> erase(final Type type) {
        return switch (type) {
            case Class<?> c -> c;
            case ParameterizedType p -> (Class<?>) p.getRawType();
            case TypeVariable<?> v -> erase(v.getBounds()[0]);
            default -> throw new IllegalArgumentException("No single class for " + type.getTypeName());
        };
    }

    /// The type [#parameterized] makes.
    private record Parameterized(Class<?> raw, List<Type> arguments) implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return arguments.toArray(Type[]::new);
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public @Nullable Type getOwnerType() {
            return raw.getDeclaringClass();
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof ParameterizedType parameterized
                && raw.equals(parameterized.getRawType())
                && Objects.equals(getOwnerType(), parameterized.getOwnerType())
                && Arrays.equals(getActualTypeArguments(), parameterized.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(getActualTypeArguments()) ^ Objects.hashCode(getOwnerType()) ^ raw.hashCode();
        }

        @Override
        public String toString() {
            return raw.getTypeName() + arguments.stream().map(Type::getTypeName).collect(Collectors.joining(", ", "<", ">"));
        }
    }

    /// A lowercase enum name, reported as a [DataResult] error rather than thrown when unknown.
    public static <E extends Enum<E>> Codec<E> enumCodec(final Class<E> enumCls) {
        return Codec.STRING.comapFlatMap(
            name -> {
                E value = enumValueOfOrNull(enumCls, name);

                return value == null
                    ? DataResult.error(() -> "Unknown " + enumCls.getSimpleName() + ": " + name)
                    : DataResult.success(value);
            },
            value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    private ParseUtils() {}

    /// The registry's holder of `sound` when it is registered as is, or a direct holder carrying it.
    public static Holder<SoundEvent> holderOf(final SoundEvent sound) {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceKey.create(Registries.SOUND_EVENT, sound.location()))
            .filter(registered -> registered.value().equals(sound))
            .<Holder<SoundEvent>>map(registered -> registered)
            .orElseGet(() -> Holder.direct(sound));
    }
}
