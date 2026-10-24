package com.yesman.akythera.api.data.modularize;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryEntryLookupEvent;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
/// @param streamCodec Null when a variable of this type cannot cross the wire, which forces every
///                    variable declaring it to a sync direction of none
/// @param element The type of each value a `List`, `Set`, `Holder` or `ResourceKey` type carries, and of each
///                value a `Map` type maps to. Null for every other type
/// @param key The type of each key a `Map` type carries. Null for every other type
/// @param registry The data pack registry whose entries this type's values are, written by their id. Null
///                 for a type that is no registry entry
public record ScriptType(
    Class<?> type,
    @Nullable Codec<?> codec,
    @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes,
    @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ?> streamCodec,
    @Nullable ScriptType element,
    @Nullable ScriptType key,
    @Nullable ResourceKey<? extends Registry<?>> registry
) {
    /// A registered type by its id, and a list, set or map type by its [Container] and the types it is made of.
    public static final Codec<ScriptType> CODEC = Codec.recursive("ScriptType", self -> Codec.either(
        AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec(),
        Container.CODEC.dispatch("container", ScriptType::container, container -> container.codec(self))
    ).flatXmap(
        either -> DataResult.success(Either.unwrap(either)),
        type -> {
            if (AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type) != null) return DataResult.success(Either.left(type));
            if (type.container() == Container.SINGLE) {
                return DataResult.error(() -> type.type().getName() + " is no registered script type");
            }

            return DataResult.success(Either.right(type));
        }
    ));

    /// [#keyOf]'s types, by element.
    private static final Map<ScriptType, ScriptType> KEY_TYPES = new ConcurrentHashMap<>();

    /// [#listOf]'s types, by element.
    private static final Map<ScriptType, ScriptType> LIST_TYPES = new ConcurrentHashMap<>();

    /// [#setOf]'s types, by element.
    private static final Map<ScriptType, ScriptType> SET_TYPES = new ConcurrentHashMap<>();

    /// [#mapOf]'s types, by key and value.
    private static final Map<List<ScriptType>, ScriptType> MAP_TYPES = new ConcurrentHashMap<>();

    // Expose the type only to engine. Won't be used in AkytheraLabs
    public ScriptType(Class<?> type) {
        this(type, null, Map.of(), null);
    }

    // Expose the type to both in engine and editor. Used for script variables that doesn't support explicit type conversion.
    public ScriptType(Class<?> type, @Nullable Codec<?> codec) {
        this(type, codec, Map.of(), null);
    }

    /// A variable type that crosses the wire.
    public ScriptType(
        Class<?> type,
        @Nullable Codec<?> codec,
        @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ?> streamCodec
    ) {
        this(type, codec, Map.of(), streamCodec);
    }

    // Expose the type to both in engine and editor. Used for script variables
    public ScriptType(Class<?> type, @Nullable Codec<?> codec, @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes) {
        this(type, codec, implicitlyCastableTypes, null);
    }

    public ScriptType(
        Class<?> type,
        @Nullable Codec<?> codec,
        @NonNull Map<Class<?>, Function<?, ?>> implicitlyCastableTypes,
        @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ?> streamCodec
    ) {
        this(type, codec, implicitlyCastableTypes, streamCodec, null, null, null);
    }

    /// Whether a variable of this type may be synced at all.
    public boolean isSyncable() {
        return streamCodec != null;
    }

    /// The collection this type is, or [Container#SINGLE] for any other type.
    public Container container() {
        if (element == null) return Container.SINGLE;
        if (type == List.class) return Container.LIST;
        if (type == Set.class) return Container.SET;
        if (type == Map.class && key != null) return Container.MAP;

        return Container.SINGLE;
    }

    /// The type this type is made of, see [Container#wrap]: the element of a list or set, the key of a map, and
    /// this type itself for any other container.
    public ScriptType base() {
        return switch (container()) {
            case SINGLE -> this;
            case LIST, SET -> Objects.requireNonNull(element);
            case MAP -> Objects.requireNonNull(key);
        };
    }

    /// A copy of `value` a script can change in place for a list, set or map type, an empty one when
    /// `value` is null. `value` itself for any other type.
    public @Nullable Object mutableCopy(final @Nullable Object value) {
        return switch (container()) {
            case SINGLE -> value;
            case LIST -> value == null ? new ArrayList<>() : new ArrayList<>((Collection<?>) value);
            case SET -> value == null ? new LinkedHashSet<>() : new LinkedHashSet<>((Collection<?>) value);
            case MAP -> value == null ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<?, ?>) value);
        };
    }

    /// Writes `value` through [#streamCodec]. An entity travels as its id, since a stream codec holds no
    /// level to resolve it by.
    ///
    /// @throws IllegalStateException when this type is neither an entity nor syncable
    @SuppressWarnings("unchecked")
    public void writeSynced(final RegistryFriendlyByteBuf buffer, final @Nullable Object value) {
        if (Entity.class.isAssignableFrom(type)) {
            buffer.writeVarInt(value instanceof Entity entity ? entity.getId() : -1);

            return;
        }

        if (!isSyncable()) throw new IllegalStateException(type.getName() + " cannot cross the wire");

        ((StreamCodec<? super RegistryFriendlyByteBuf, Object>) streamCodec).encode(buffer, value);
    }

    /// Reads a value [#writeSynced] wrote.
    ///
    /// @param level resolves an entity's id
    /// @throws IllegalStateException when this type is neither an entity nor syncable
    @SuppressWarnings("unchecked")
    public <T> @Nullable T readSynced(final RegistryFriendlyByteBuf buffer, final Level level) {
        if (Entity.class.isAssignableFrom(type)) {
            int entityId = buffer.readVarInt();

            return entityId < 0 ? null : (T) level.getEntity(entityId);
        }

        if (!isSyncable()) throw new IllegalStateException(type.getName() + " cannot cross the wire");

        return (T) streamCodec.decode(buffer);
    }

    /// `json` decoded through [#codec].
    ///
    /// @throws IllegalStateException when this type has no codec, or `json` does not decode
    @SuppressWarnings("unchecked")
    public <T> T parse(final JsonElement json) {
        if (codec == null) throw new IllegalStateException(type.getName() + " has no codec to decode " + json);

        return ((Codec<T>) codec).parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
    }

    /// A value that is an entry of the data pack registry `registry`, written as its id. Both ways are
    /// looked up by [BuiltInEventHooks.Registry] subscribers first, then the live registries.
    public static <T> ScriptType registryEntry(final Class<T> type, final ResourceKey<? extends Registry<T>> registry) {
        Codec<T> codec = ResourceKey.codec(registry).flatXmap(
            key -> lookupEntry(key)
                .map(holder -> DataResult.success(holder.value()))
                .orElseGet(() -> DataResult.error(() -> "No " + registry.identifier() + " entry " + key.identifier())),
            value -> entryKey(registry, value)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> value + " is not registered in " + registry.identifier()))
        );

        return new ScriptType(type, codec, Map.of(), null, null, null, registry);
    }

    public static <T> Optional<ResourceKey<T>> entryKey(final ResourceKey<? extends Registry<T>> registry, final T value) {
        RegistryEntryLookupEvent.ByValue<T> event = new RegistryEntryLookupEvent.ByValue<>(registry, value);
        BuiltInEventHooks.Registry.LOOKUP_ENTRY_BY_VALUE.post(event);

        return Optional.ofNullable(event.getResult())
            .or(() -> StaticRegistryAccess.getRegistryAccess().lookupOrThrow(registry).getResourceKey(value));
    }

    /// The entry `key` names, answered by [BuiltInEventHooks.Registry#LOOKUP_ENTRY_BY_KEY] subscribers
    /// first, then the live registries.
    public static <T> Optional<Holder<T>> lookupEntry(final ResourceKey<T> key) {
        RegistryEntryLookupEvent.ByKey<T> event = new RegistryEntryLookupEvent.ByKey<>(key);
        BuiltInEventHooks.Registry.LOOKUP_ENTRY_BY_KEY.post(event);

        return Optional.ofNullable(event.getResult()).or(() -> StaticRegistryAccess.getRegistryAccess().get(key));
    }

    /// A list of `element`s. The same instance for the same `element`.
    @SuppressWarnings("unchecked")
    public static ScriptType listOf(final ScriptType element) {
        return LIST_TYPES.computeIfAbsent(element, _ -> {
            Codec<?> codec = element.codec == null ? null : ((Codec<Object>) element.codec).listOf();
            StreamCodec<RegistryFriendlyByteBuf, ArrayList<Object>> streamCodec = element.streamCodec == null
                ? null
                : ByteBufCodecs.collection(
                    ArrayList::new, (StreamCodec<? super RegistryFriendlyByteBuf, Object>) element.streamCodec);

            return new ScriptType(List.class, codec, Map.of(), streamCodec, element, null, null);
        });
    }

    /// A set of `element`s, written as a list. The same instance for the same `element`.
    @SuppressWarnings("unchecked")
    public static ScriptType setOf(final ScriptType element) {
        return SET_TYPES.computeIfAbsent(element, _ -> {
            Codec<?> codec = element.codec == null
                ? null
                : ((Codec<Object>) element.codec).listOf().xmap(Set::copyOf, List::copyOf);
            StreamCodec<RegistryFriendlyByteBuf, LinkedHashSet<Object>> streamCodec = element.streamCodec == null
                ? null
                : ByteBufCodecs.collection(
                    LinkedHashSet::new, (StreamCodec<? super RegistryFriendlyByteBuf, Object>) element.streamCodec);

            return new ScriptType(Set.class, codec, Map.of(), streamCodec, element, null, null);
        });
    }

    /// A map from `key`s to `value`s, written as a list of `key` / `value` entries. The same instance for the
    /// same `key` and `value`.
    @SuppressWarnings("unchecked")
    public static ScriptType mapOf(final ScriptType key, final ScriptType value) {
        return MAP_TYPES.computeIfAbsent(List.of(key, value), _ -> {
            Codec<?> codec = key.codec == null || value.codec == null
                ? null
                : Codec.mapPair(((Codec<Object>) key.codec).fieldOf("key"), ((Codec<Object>) value.codec).fieldOf("value"))
                    .codec()
                    .listOf()
                    .xmap(
                        entries -> {
                            Map<Object, Object> map = new LinkedHashMap<>();
                            entries.forEach(entry -> map.put(entry.getFirst(), entry.getSecond()));

                            return Collections.unmodifiableMap(map);
                        },
                        map -> map.entrySet().stream().map(entry -> Pair.of(entry.getKey(), entry.getValue())).toList()
                    );
            StreamCodec<RegistryFriendlyByteBuf, Map<Object, Object>> streamCodec =
                key.streamCodec == null || value.streamCodec == null
                ? null
                : ByteBufCodecs.map(
                    LinkedHashMap::new,
                    (StreamCodec<? super RegistryFriendlyByteBuf, Object>) key.streamCodec,
                    (StreamCodec<? super RegistryFriendlyByteBuf, Object>) value.streamCodec
                );

            return new ScriptType(Map.class, codec, Map.of(), streamCodec, value, key, null);
        });
    }

    /// A reference to an entry of `element`'s registry, written as its id. Null when `element` is no
    /// registry entry.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable ScriptType holderOf(final ScriptType element) {
        if (element.registry == null) return null;

        ResourceKey registry = element.registry;
        Codec<Holder<?>> codec = ResourceKey.codec(registry).flatXmap(
            key -> lookupEntry((ResourceKey) key)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "No " + registry.identifier() + " entry " + key)),
            holder -> ((Holder<?>) holder).unwrapKey()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "A direct holder has no id: " + holder))
        );

        return new ScriptType(Holder.class, codec, Map.of(), null, element, null, null);
    }

    /// The [ResourceKey] of an entry of `element`'s registry, written as its id. Null when `element` is no
    /// registry entry. The same instance for the same `element`.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable ScriptType keyOf(final ScriptType element) {
        if (element.registry == null) return null;

        return KEY_TYPES.computeIfAbsent(element, _ ->
            new ScriptType(ResourceKey.class, ResourceKey.codec((ResourceKey) element.registry), Map.of(), null, element, null, null));
    }

    /// The registered [ScriptType] for `type` (boxing-aware), or null when it isn't registered.
    public static @Nullable ScriptType of(Class<?> type) {
        Class<?> boxed = ParseUtils.box(type);

        for (ScriptType scriptType : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (ParseUtils.box(scriptType.type()) == boxed) return scriptType;
        }

        return null;
    }

    /// The type a generic declaration names: a registered type by its class, a `List` or `Set` of a registered
    /// type, a `Map` between registered types, or a `Holder` or `ResourceKey` of a registry entry type. Any
    /// other parameterized type, and a `Map` with an unregistered part, resolves by its raw class, a wildcard or
    /// a type variable by its bound. Null when a part of a list, set, holder or key is unregistered.
    public static @Nullable ScriptType of(Type type) {
        return switch (type) {
            case Class<?> c -> of(c);
            case ParameterizedType p when p.getRawType() == Map.class && p.getActualTypeArguments().length == 2 -> {
                ScriptType key = of(p.getActualTypeArguments()[0]);
                ScriptType value = of(p.getActualTypeArguments()[1]);

                yield key == null || value == null ? of(Map.class) : mapOf(key, value);
            }
            case ParameterizedType p when p.getActualTypeArguments().length == 1
                && (p.getRawType() == List.class || p.getRawType() == Set.class
                    || p.getRawType() == Holder.class || p.getRawType() == ResourceKey.class) -> {
                ScriptType element = of(p.getActualTypeArguments()[0]);

                if (element == null) yield null;
                if (p.getRawType() == List.class) yield listOf(element);
                if (p.getRawType() == Set.class) yield setOf(element);
                if (p.getRawType() == ResourceKey.class) yield keyOf(element);

                yield holderOf(element);
            }
            case ParameterizedType p -> of(p.getRawType());
            case WildcardType w -> w.getUpperBounds().length == 1 ? of(w.getUpperBounds()[0]) : null;
            case TypeVariable<?> v -> v.getBounds().length == 1 ? of(v.getBounds()[0]) : null;
            default -> null;
        };
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

    /// The collection a [ScriptType] is.
    public enum Container {
        SINGLE,
        LIST,
        SET,
        MAP;

        public static final Codec<Container> CODEC = ParseUtils.enumCodec(Container.class);

        /// The type of this container made of `base`: `base` itself for a single value, a list or set of
        /// `base`, or a map from `base` to `value`.
        public ScriptType wrap(final ScriptType base, final ScriptType value) {
            return switch (this) {
                case SINGLE -> base;
                case LIST -> listOf(base);
                case SET -> setOf(base);
                case MAP -> mapOf(base, value);
            };
        }

        /// The fields [ScriptType#CODEC] writes beside the container name.
        private MapCodec<ScriptType> codec(final Codec<ScriptType> self) {
            return switch (this) {
                case SINGLE -> self.fieldOf("type");
                case LIST -> self.fieldOf("element").xmap(ScriptType::listOf, ScriptType::element);
                case SET -> self.fieldOf("element").xmap(ScriptType::setOf, ScriptType::element);
                case MAP -> RecordCodecBuilder.mapCodec(
                    instance ->
                        instance.group(
                            self.fieldOf("key").forGetter(ScriptType::key),
                            self.fieldOf("value").forGetter(ScriptType::element)
                        ).apply(instance, ScriptType::mapOf)
                );
            };
        }
    }
}
