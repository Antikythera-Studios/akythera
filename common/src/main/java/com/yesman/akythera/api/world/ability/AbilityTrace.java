package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.Akythera;
import org.jspecify.annotations.Nullable;

/// Writes an [AbilityInstance]'s lifecycle to the log.
///
/// Must be used only for the development stage and removed on releases.
public final class AbilityTrace {
    private AbilityTrace() {}

    /// Writes one line naming the ability, its slot, the phase it entered and the side it ran on.
    /// Silent unless [AbilitySystemComponent#isTracing] is set.
    public static void log(
        final AbilitySystemComponent abilitySystemComponent,
        final @Nullable AbilityInstance abilityInstance,
        final String phase
    ) {
        if (!abilitySystemComponent.isTracing()) return;

        Ability<?> ability = abilityInstance == null ? null : abilityInstance.getAbility();

        Akythera.LOGGER.info(
            "ability {} {} in {} ({})",
            ability == null ? "<none>" : ability.getClass().getSimpleName(),
            phase,
            abilityInstance == null ? "<no slot>" : abilityInstance.getSlot(),
            abilitySystemComponent.getOwner().isLogicalClient() ? "client" : "server"
        );
    }
}
