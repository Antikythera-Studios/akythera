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

    /// A type whose identity is its path alone, under one namespace.
    ///
    /// An entry is a terminal tag and its ancestor segments are path tags: `activation/combo_attack`
    /// registers the terminal `activation/combo_attack` and the path tag `activation`. Path tags are
    /// shared by any number of entries, but a terminal may not land on a registered path, terminal or
    /// path tag. Code registration and data pack loading both throw when an entry breaks this.
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

    private int register(final T value, final Identifier declaredIdentifier, final boolean hardcoded) {
        Identifier enumIdentifier = canonical(declaredIdentifier);

        if (enumsByIdentifier.containsKey(enumIdentifier)) {
            throw new IllegalArgumentException("Enum identifier " + enumIdentifier + " already exists in " + enumType + ". Consider another enum name.");
        }

        if (hardcoded && implicitParentFactory != null) {
            Set<String> registered = new HashSet<>();
            enumsByIdentifier.keySet().forEach(identifier -> registered.add(identifier.getPath()));
            String terminal = terminalAncestorOf(enumIdentifier.getPath(), registered);

            if (terminal != null) {
                throw new IllegalArgumentException(
                    "Enum identifier " + enumIdentifier + " sits under the terminal entry " + terminal + " in " + enumType);
            }
        }

        int enumOrdinal = lastOrdinal;

        enumsByOrdinal.put(enumOrdinal, value);
        enumsByIdentifier.put(enumIdentifier, value);
        if (hardcoded) hardcodedEnums.add(value);
        ++lastOrdinal;

        // A code-registered path-identified entry registers its parent as a path tag, which does the same for its own.
        if (hardcoded && implicitParentFactory != null) {
            String path = enumIdentifier.getPath();
            int cut = path.lastIndexOf('/');

            if (cut > 0) {
                Identifier parent = Identifier.fromNamespaceAndPath(canonicalNamespace, path.substring(0, cut));
                if (!enumsByIdentifier.containsKey(parent)) register(implicitParentFactory.get(), parent, true);
            }
        }

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

    /// Rewrites `parsed` into what a path-identified type actually registers: one namespace, and a
    /// path tag for every ancestor segment no code-registered entry covers. Other types pass through
    /// untouched.
    ///
    /// @throws IllegalStateException when an entry breaks the path rule of
    /// [#EnumerableDataBlockManager(String, MapCodec, Supplier)] against the other entries or the
    /// code-registered ones
    @ApiStatus.Internal
    public Map<Identifier, T> canonicalizeDataPackEntries(final Map<Identifier, T> parsed) {
        if (canonicalNamespace == null || implicitParentFactory == null) return parsed;

        Set<String> hardcodedPaths = new HashSet<>();
        enumsByIdentifier.forEach((identifier, value) -> {
            if (hardcodedEnums.contains(value)) hardcodedPaths.add(identifier.getPath());
        });

        Map<String, Identifier> declaredBy = new HashMap<>();
        Set<String> parentPaths = new HashSet<>();

        for (Identifier declaring : parsed.keySet()) {
            String path = declaring.getPath();
            Identifier declared = declaredBy.putIfAbsent(path, declaring);

            if (declared != null) {
                throw new IllegalStateException(
                    "Two " + enumType + " entries resolve to the path " + path + ": " + declared + " and " + declaring
                );
            }

            for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
                parentPaths.add(path.substring(0, cut));
            }
        }

        Map<Identifier, T> canonical = new TreeMap<>();

        for (Map.Entry<Identifier, T> entry : parsed.entrySet()) {
            String path = entry.getKey().getPath();

            if (hardcodedPaths.contains(path) || parentPaths.contains(path)) {
                throw new IllegalStateException(
                    "The " + enumType + " entry " + entry.getKey() + " lands on " + path + ", which is already a registered path"
                );
            }

            String terminal = terminalAncestorOf(path, hardcodedPaths);

            if (terminal != null) {
                throw new IllegalStateException(
                    "The " + enumType + " entry " + entry.getKey() + " sits under the built-in terminal entry " + terminal
                );
            }

            canonical.put(Identifier.fromNamespaceAndPath(canonicalNamespace, path), entry.getValue());
        }

        for (String parent : parentPaths) {
            if (!hardcodedPaths.contains(parent)) {
                canonical.put(Identifier.fromNamespaceAndPath(canonicalNamespace, parent), implicitParentFactory.get());
            }
        }

        return canonical;
    }

    /// The nearest ancestor of `path` that `registered` holds as a terminal, with nothing beneath it.
    /// Null when there is none.
    private static @Nullable String terminalAncestorOf(final String path, final Set<String> registered) {
        for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
            String ancestor = path.substring(0, cut);

            if (registered.contains(ancestor) && registered.stream().noneMatch(other -> other.startsWith(ancestor + "/"))) {
                return ancestor;
            }
        }

        return null;
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

    /// Resolves by path alone when [#isPathIdentified].
    public T getOrThrow(final Identifier identifier) throws NoSuchElementException {
        T value = enumsByIdentifier.get(canonical(identifier));

        if (value == null) {
            throw new NoSuchElementException("Enum identifier " + identifier + " does not exist in " + enumType);
        }

        return value;
    }

    public Optional<T> get(final int id) {
        return Optional.ofNullable(enumsByOrdinal.get(id));
    }

    /// Resolves by path alone when [#isPathIdentified].
    public Optional<T> get(final Identifier name) {
        return Optional.ofNullable(enumsByIdentifier.get(canonical(name)));
    }

    /// Whether entries are told apart by their path alone, under one namespace. See
    /// [#EnumerableDataBlockManager(String, MapCodec, Supplier)] for the path rule they follow.
    public boolean isPathIdentified() {
        return canonicalNamespace != null;
    }

    /// `identifier` under the namespace this manager registers it by.
    private Identifier canonical(final Identifier identifier) {
        return canonicalNamespace == null
            ? identifier
            : Identifier.fromNamespaceAndPath(canonicalNamespace, identifier.getPath());
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