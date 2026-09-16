package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// The hand-curated [AbilityCue] registry, keyed by [AbilityCue#getId].
///
/// Entries are registered at the end of mod initialization, after the [AbilityTag] enums load,
/// and the registry is frozen straight after.
public final class AbilityCues {
    private static final Map<Identifier, AbilityCue> REGISTRY = new LinkedHashMap<>();

    private static boolean frozen;

    private AbilityCues() {}

    /// @throws IllegalStateException after the registry is frozen, or when the cue's id is taken
    public static void register(final AbilityCue cue) {
        if (frozen) {
            throw new IllegalStateException("AbilityCue registry is frozen: " + cue.getId());
        }

        if (REGISTRY.putIfAbsent(cue.getId(), cue) != null) {
            throw new IllegalStateException("Duplicate ability cue: " + cue.getId());
        }
    }

    /// The cue `identifier` names: a registered one first, then the data pack registry.
    public static @Nullable AbilityCue get(final Identifier identifier) {
        AbilityCue registered = REGISTRY.get(identifier);

        if (registered != null) return registered;

        RegistryAccess registryAccess = StaticRegistryAccess.getRegistryAccessUnsafe();

        // Asked before a world loaded, which a play packet arriving during a login race can do.
        if (registryAccess == null) return null;

        return registryAccess
            .lookup(AkytheraRegistries.DataPack.ABILITY_CUE_KEY)
            .flatMap(registry -> registry.get(
                ResourceKey.create(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, identifier)
            ))
            .map(AbilityCueBlueprint::createCue)
            .orElse(null);
    }

    @ApiStatus.Internal
    public static void freeze() {
        frozen = true;
    }
}
