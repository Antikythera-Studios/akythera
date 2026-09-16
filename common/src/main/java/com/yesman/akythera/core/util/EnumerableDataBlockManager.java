package com.yesman.akythera.core.util;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// A manager class for [EnumerableDataBlock]
///
/// Declare a static field of enum manager instance in the root enum interface and use the manager to assign global id
/// and dynamically load enums in order
public final class EnumerableDataBlockManager<T extends EnumerableDataBlock> {
    private final BiMap<Integer, T> enumsByOrdinal = HashBiMap.create();
    private final BiMap<Identifier, T> enumsByIdentifier = HashBiMap.create();

    /// Data blocks that are registered through code.
    /// Maintained to avoid removing when data reload occurs
    private final Set<T> hardcodedEnums = new HashSet<>();

    // Concurrent map since mod constructors are called asynchronously in NeoForged mod loader
    private final Map<String, TreeSet<Class<? extends EnumerableDataBlock>>> enumClasses = new ConcurrentHashMap<> ();

    private final String enumType;

    private final MapCodec<T> mapCodec;
    private final Supplier<Codec<T>> byNameCodec;
    private final Supplier<StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec;

    private int lastOrdinal = 0;

    /// Used for getting data pack directory
    private final ResourceKey<Registry<T>> directoryKey;

    /// The one namespace a path-identified type registers under, or null when identifiers carry
    /// their own.
    private final @Nullable String canonicalNamespace;

    /// Builds the ancestor entries a path-identified type generates, or null when this type is not
    /// one.
    private final @Nullable Supplier<T> implicitParentFactory;

    /// A type whose identity is its path alone: one namespace, implicit ancestors, and a throw when
    /// two entries land on one path.
    public EnumerableDataBlockManager(
        final String enumName,
        final MapCodec<T> mapCodec,
        final Supplier<T> implicitParentFactory
    ) {
        this(Akythera.MODID, enumName, mapCodec, Akythera.MODID, implicitParentFactory);
    }

    public EnumerableDataBlockManager(final String namespace, final String enumName, final MapCodec<T> mapCodec) {
        this(namespace, enumName, mapCodec, null, null);
    }

    private EnumerableDataBlockManager(
        final String namespace,
        final String enumName,
        final MapCodec<T> mapCodec,
        final @Nullable String canonicalNamespace,
        final @Nullable Supplier<T> implicitParentFactory
    ) {
        this.enumType = enumName;
        this.mapCodec = mapCodec;
        this.canonicalNamespace = canonicalNamespace;
        this.implicitParentFactory = implicitParentFactory;

        // codecs are memorized suppliers for non-
        byNameCodec = Suppliers.memoize(() -> Identifier.CODEC.comapFlatMap(
            identifier -> get(identifier)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> String.format("Unknown enum object %s in %s", enumName, identifier))),
            EnumerableDataBlock::identifier
        ));

        streamCodec = Suppliers.memoize(() -> new StreamCodec<> () {
            /// Written by identifier, not by ordinal. An ordinal is assigned from a counter that
            /// only ever climbs, so a client that has already loaded one world numbers data pack
            /// entries differently from the next server it joins.
            @Override
            public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull T obj) {
                Identifier identifier = derefIdentifier(obj);

                if (identifier == null) {
                    throw new IllegalStateException(
                        "No identifier registered in " + enumType + " for " + obj.getClass().getName()
                    );
                }

                Identifier.STREAM_CODEC.encode(buffer, identifier);
            }

            @Override
            public @NonNull T decode(@NonNull RegistryFriendlyByteBuf buffer) {
                return getOrThrow(Identifier.STREAM_CODEC.decode(buffer));
            }
        });

        directoryKey = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(namespace, "datablock/" + enumName));
    }

    public EnumerableDataBlockManager(final String enumName, final MapCodec<T> mapCodec) {
        this(Akythera.MODID, enumName, mapCodec);
    }

    // Data block that doesn't support the dynamic load
    public EnumerableDataBlockManager(final String enumName) {
        this(Akythera.MODID, enumName, null);
    }

    /// Register an enum class
    ///
    /// All enum classes inherits <T> must be registered to avoid naming, enum id crash
    ///
    /// This must be called in a constructor of mod entrypoint class
    public void registerEnumCls(final String modId, final Class<? extends EnumerableDataBlock> cls) {
        // Checks whether the same class has registered with different mod id.
        for (Map.Entry<String, TreeSet<Class<? extends EnumerableDataBlock>>> e : enumClasses.entrySet()) {
            if (e.getValue().contains(cls)) {
                Akythera.LOGGER.error("{} is already registered. Skipped.", cls.getCanonicalName());
                return;
            }
        }

        TreeSet<Class<? extends EnumerableDataBlock>> treeset = enumClasses.computeIfAbsent(
            modId,
            _ -> new TreeSet<>(Comparator.comparing((Class<? extends EnumerableDataBlock> c) -> c.getSimpleName()))
        );

        treeset.add(cls);
        Akythera.LOGGER.debug("Registered Extensible Enum {} for {}", enumType, modId);
    }

    /// Loads all enum objects registered via [#registerEnumCls]
    ///
    /// This must be called mod loader's lifecycle hook that initializes mod. In other words, it's called after all
    /// addons' enumerable data blocks are registered
    ///
    /// For the cases that you inherits enumerable data block from outside dependency, you don't need to call this again
    public void loadEnum() {
        List<String> orderByModId = new ArrayList<>(enumClasses.keySet());
        Collections.sort(orderByModId);
        Class<?> lastClassToLoad = null;

        try {
            for (String modId : orderByModId) {
                for (Class<? extends EnumerableDataBlock> enumClass : enumClasses.get(modId)) {
                    lastClassToLoad = enumClass;
                    Method m = lastClassToLoad.getMethod("values");
                    m.invoke(null);
                    Akythera.LOGGER.debug("Loaded enums in {}", lastClassToLoad.getSimpleName());
                }
            }
        } catch (ClassCastException e) {
            Akythera.LOGGER.error("{} is not Extensible Enum!", lastClassToLoad.getCanonicalName(), e);
        } catch (NoSuchMethodException e) {
            Akythera.LOGGER.error("{} is not an Enum class!", lastClassToLoad.getCanonicalName(), e);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Akythera.LOGGER.warn("Error while loading enumerable data block {}", lastClassToLoad.getCanonicalName(), e);
        }

        Akythera.LOGGER.debug("Enum objects {} loaded successfully: {}", enumType, enumsByIdentifier.values());
    }

    /// Returns the next enum id
    ///
    /// Assign the id on enum constructor, they must be created by [#loadEnum()] initializer
    /// to avoid inconsistent ID assignment among the terminals (local client, remote clients, and server)
    @ApiStatus.Internal
    public int assign(final T value, @Nullable Identifier dynamicIdentifier, final boolean hardcoded) {
        Identifier enumIdentifier = hardcoded
            ? Identifier.fromNamespaceAndPath(ownerModId(value), ParseUtils.toLowerCase(((Enum<?>)value).name()))
            : dynamicIdentifier;

        return register(value, enumIdentifier, hardcoded);
    }

    /// Convention for registering hardcoded enums
    public int assign(final T value) {
        return assign(value, null, true);
    }

    /// Registers a hardcoded enum under `identifierPath` instead of the [Enum#name()]-derived path,
    /// keeping the namespace of the mod that registered the enum class.
    ///
    /// Generated enums use this for entries whose identifier carries a directory (`humanoid/idle`),
    /// which an enum constant name cannot spell.
    public int assign(final T value, final String identifierPath) {
        return register(value, Identifier.fromNamespaceAndPath(ownerModId(value), identifierPath), true);
    }

    /// The mod id that passed `value`'s class to [#registerEnumCls]
    private String ownerModId(final T value) {
        for (Map.Entry<String, TreeSet<Class<? extends EnumerableDataBlock>>> entry : enumClasses.entrySet()) {
            for (Class<? extends EnumerableDataBlock> cls : entry.getValue()) {
                if (value.getClass() == cls) {
                    return entry.getKey();
                }
            }
        }

        throw new IllegalArgumentException("Can't find registered owner class: " + value.getClass().getSimpleName());
    }

    private int register(final T value, final Identifier enumIdentifier, final boolean hardcoded) {
        if (enumsByIdentifier.containsKey(enumIdentifier)) {
            throw new IllegalArgumentException("Enum identifier " + enumIdentifier + " already exists in " + enumType + ". Consider another enum name.");
        }

        int enumOrdinal = lastOrdinal;

        enumsByOrdinal.put(enumOrdinal, value);
        enumsByIdentifier.put(enumIdentifier, value);
        if (hardcoded) hardcodedEnums.add(value);
        ++lastOrdinal;

        return enumOrdinal;
    }

    /// Binds `entries` as this manager's data pack contents, **keeping the instance already bound to
    /// an identifier that is still declared**.
    ///
    /// These blocks are stateless tokens whose identity is the binding itself ([#derefIdentifier] is
    /// an identity lookup), and live game objects hold the instance -- an `AnimationStateMachine` keeps
    /// the state it was built with. Rebinding a re-declared identifier to the freshly decoded stand-in
    /// would orphan every one of those, so the stand-in is dropped and the original kept, which also
    /// keeps its ordinal stable across reloads (ordinals travel on the wire, see [#streamCodec]).
    ///
    /// @param entries A data block entry sorted by key
    @ApiStatus.Internal
    public void loadDataPackEntries(final Map<Identifier, T> entries) {
        // Entries the last load declared and this one does not are gone; drop them and their ordinals.
        enumsByIdentifier.entrySet().removeIf(
            entry -> !hardcodedEnums.contains(entry.getValue()) && !entries.containsKey(entry.getKey()));
        enumsByOrdinal.entrySet().removeIf(
            entry -> !hardcodedEnums.contains(entry.getValue())
                && !enumsByIdentifier.containsValue(entry.getValue()));

        for (Map.Entry<Identifier, T> entry : entries.entrySet()) {
            if (enumsByIdentifier.containsKey(entry.getKey())) continue;

            register(entry.getValue(), entry.getKey(), false);
        }
    }

    /// Rewrites `parsed` into what a path-identified type actually registers: one namespace, no two
    /// entries on one path, no entry shadowing a code-registered entry, and an entry for every
    /// ancestor path no file declared. Other types pass through untouched.
    ///
    /// @throws IllegalStateException when two entries resolve to the same path
    @ApiStatus.Internal
    public Map<Identifier, T> canonicalizeDataPackEntries(final Map<Identifier, T> parsed) {
        if (canonicalNamespace == null || implicitParentFactory == null) return parsed;

        Map<String, Identifier> declaredBy = new HashMap<>();
        Map<Identifier, T> canonical = new TreeMap<>();

        for (Map.Entry<Identifier, T> entry : parsed.entrySet()) {
            String path = entry.getKey().getPath();
            Identifier declared = declaredBy.putIfAbsent(path, entry.getKey());

            if (declared != null) {
                throw new IllegalStateException(
                    "Two " + enumType + " entries resolve to the path " + path + ": "
                        + declared + " and " + entry.getKey()
                );
            }

            Identifier identifier = Identifier.fromNamespaceAndPath(canonicalNamespace, path);
            T registered = enumsByIdentifier.get(identifier);

            // A code-registered entry survives reloads and a data pack one does not, so the
            // hardcoded entry keeps the path.
            if (registered != null && hardcodedEnums.contains(registered)) {
                Akythera.LOGGER.warn(
                    "Data pack {} declares {}, which a built-in entry already owns. Dropped.",
                    entry.getKey(), path
                );

                continue;
            }

            canonical.put(identifier, entry.getValue());
        }

        for (Identifier identifier : Set.copyOf(canonical.keySet())) {
            addImplicitParents(canonical, identifier.getPath());
        }

        return canonical;
    }

    /// Adds an entry for every ancestor segment of `path` that neither a file nor code declared.
    private void addImplicitParents(final Map<Identifier, T> canonical, final String path) {
        int cut = path.lastIndexOf('/');

        while (cut > 0) {
            String parent = path.substring(0, cut);
            Identifier identifier = Identifier.fromNamespaceAndPath(canonicalNamespace, parent);

            if (canonical.containsKey(identifier) || enumsByIdentifier.containsKey(identifier)) return;

            canonical.put(identifier, implicitParentFactory.get());
            cut = parent.lastIndexOf('/');
        }
    }


    @ApiStatus.Internal
    public void clearDataPackEntries() {
        // Clear non-datapack objects
        enumsByOrdinal.entrySet().removeIf(entry -> !hardcodedEnums.contains(entry.getValue()));
        enumsByIdentifier.entrySet().removeIf(entry -> !hardcodedEnums.contains(entry.getValue()));
    }

    public T getOrThrow(final int id) throws NoSuchElementException {
        if (!enumsByOrdinal.containsKey(id)) {
            throw new NoSuchElementException("Enum id " + id + " does not exist in " + enumType);
        }

        return enumsByOrdinal.get(id);
    }

    public T getOrThrow(final Identifier identifier) throws NoSuchElementException {
        if (!enumsByIdentifier.containsKey(identifier)) {
            throw new NoSuchElementException("Enum identifier " + identifier + " does not exist in " + enumType);
        }

        return enumsByIdentifier.get(identifier);
    }

    public Optional<T> get(final int id) {
        return Optional.ofNullable(enumsByOrdinal.get(id));
    }

    public Optional<T> get(final Identifier name) {
        return Optional.ofNullable(enumsByIdentifier.get(name));
    }

    /// The ordinal `object` is registered under.
    ///
    /// @throws IllegalStateException if it is not registered -- an instance that outlived its data pack
    /// entry, which [#derefIdentifier] reports as a null identifier.
    public int derefOrdinal(final T object) {
        Integer ordinal = enumsByOrdinal.inverse().get(object);

        if (ordinal == null) {
            throw new IllegalStateException("No ordinal registered in " + enumType + " for " + object.getClass().getName()
                + ": the object is not (or no longer) a registered data block");
        }

        return ordinal;
    }

    /// Returns identifier of given enum object
    ///
    /// Mutual-transformable with [#get(Identifier)] and [#getOrThrow(Identifier)]
    ///
    /// Null when `enumObject` is not registered here -- an instance that outlived its data pack entry,
    /// which a live game object can hold after a reload dropped the entry it was built from.
    @SuppressWarnings("unchecked")
    public @Nullable Identifier derefIdentifier(final EnumerableDataBlock enumObject) {
        return enumsByIdentifier.inverse().get((T)enumObject);
    }

    public Codec<T> getNameCodec() {
        return byNameCodec.get();
    }

    /// The data block type's name, which is also its data pack directory.
    public String getEnumType() {
        return enumType;
    }

    public MapCodec<T> getMapCodec() {
        return mapCodec;
    }

    public StreamCodec<? super RegistryFriendlyByteBuf, T> getStreamCodec() {
        return streamCodec.get();
    }

    public ResourceKey<Registry<T>> directoryKey() {
        return directoryKey;
    }

    /// Returns all enum objects belongs to the manager
    public Collection<T> universalValues() {
        return enumsByOrdinal.values();
    }

    /// Returns translatable key of the enum object
    public Component toTranslatable(final EnumerableDataBlock e) {
        return Component.translatable(String.format("extensible_enum.%s.%s.%s", e.identifier().getNamespace(), enumType, e.identifier().getPath()));
    }
}