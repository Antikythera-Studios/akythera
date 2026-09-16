package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/// The hand-curated [AbilityResource] registry.
///
/// Entries are registered during mod initialization and the registry is frozen once every plugin
/// has had its turn. Registering after the freeze throws.
public final class AbilityResources {
    private static final Map<Identifier, AbilityResource> REGISTRY = new LinkedHashMap<>();
    private static final Map<AbilityResource, Identifier> IDENTIFIERS = new HashMap<>();

    private static boolean frozen;

    public static final Codec<AbilityResource> CODEC =
        Identifier.CODEC.comapFlatMap(
            identifier -> {
                AbilityResource resource = REGISTRY.get(identifier);

                return resource == null
                    ? DataResult.error(() -> "Unknown ability resource: " + identifier)
                    : DataResult.success(resource);
            },
            AbilityResources::getIdOrThrow
        );

    public static final StreamCodec<? super RegistryFriendlyByteBuf, AbilityResource> STREAM_CODEC =
        Identifier.STREAM_CODEC.map(AbilityResources::getOrThrow, AbilityResources::getIdOrThrow);

    private AbilityResources() {}

    /// Registers `resource` under `identifier`.
    ///
    /// @throws IllegalStateException after the registry is frozen, or when `identifier` is taken
    public static void register(final Identifier identifier, final AbilityResource resource) {
        if (frozen) {
            throw new IllegalStateException("AbilityResource registry is frozen: " + identifier);
        }

        if (REGISTRY.containsKey(identifier)) {
            throw new IllegalStateException("Duplicate ability resource: " + identifier);
        }

        REGISTRY.put(identifier, resource);
    }

    public static @Nullable AbilityResource get(final Identifier identifier) {
        return REGISTRY.get(identifier);
    }

    public static AbilityResource getOrThrow(final Identifier identifier) {
        AbilityResource resource = REGISTRY.get(identifier);

        if (resource == null) {
            throw new NoSuchElementException("Unknown ability resource: " + identifier);
        }

        return resource;
    }

    public static @Nullable Identifier getId(final AbilityResource resource) {
        return IDENTIFIERS.get(resource);
    }

    public static Identifier getIdOrThrow(final AbilityResource resource) {
        Identifier identifier = IDENTIFIERS.get(resource);

        if (identifier == null) {
            throw new NoSuchElementException("Unregistered ability resource: " + resource);
        }

        return identifier;
    }

    public static Stream<Map.Entry<Identifier, AbilityResource>> entryStream() {
        return REGISTRY.entrySet().stream();
    }

    /// Registers the entries Akythera owns. Runs before third-party plugins register theirs.
    @ApiStatus.Internal
    public static void bootStrap() {
        register(Akythera.identifier("health"), new Health());
    }

    /// Builds the reverse lookup and closes the registry to further entries.
    @ApiStatus.Internal
    public static void freeze() {
        IDENTIFIERS.clear();
        REGISTRY.forEach((identifier, resource) -> IDENTIFIERS.put(resource, identifier));
        frozen = true;
    }

    /// An entity's health, bounded by its max_health attribute and written through
    /// [LivingEntity#setHealth].
    private static final class Health implements AbilityResource {
        @Override
        public float read(final LivingEntityPatch<?> owner) {
            return owner.getOriginal().getHealth();
        }

        @Override
        public void write(final LivingEntityPatch<?> owner, final float value) {
            owner.getOriginal().setHealth(value);
        }

        @Override
        public float clamp(final LivingEntityPatch<?> owner, final float value) {
            return Math.clamp(value, 0.0F, owner.getOriginal().getMaxHealth());
        }

        @Override
        public boolean appliesTo(final LivingEntityPatch<?> owner) {
            return true;
        }

        @Override
        public Kind kind() {
            return Kind.CONSUMPTIVE;
        }

        /// Zero health is death.
        @Override
        public boolean canEmpty() {
            return false;
        }
    }
}
