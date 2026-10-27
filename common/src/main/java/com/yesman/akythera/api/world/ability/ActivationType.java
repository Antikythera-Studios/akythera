package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptType;

/// A [SystematicAbility] preset wiring activation requests to the ability lifecycle. A bare [Ability]
/// has no activation type.
@ScriptType(autoRegister = false)
public enum ActivationType {
    /// Activates, then ends on the same tick.
    INSTANT,

    /// Activates, then finishes when the Duration Effect expires.
    DURATION,

    /// The first request activates, the second finishes.
    HOLD,

    /// The first request activates, the second cancels.
    TOGGLE;

    /// Whether a second activation request on a running ability cancels it rather than recasting.
    public boolean recastCancels() {
        return this == TOGGLE;
    }

    /// Whether a second activation request on a running ability finishes it rather than recasting.
    ///
    /// The second request on a Hold preset is the release.
    public boolean recastFinishes() {
        return this == HOLD;
    }

    /// Whether a cost of `applicationType` is legal on this preset.
    ///
    /// There is no Duration cost. An Infinite cost is drained every tick the ability runs, which
    /// only the presets that stay activated by input can offer.
    public boolean allowsCostType(final AbilityEffect.ApplicationType applicationType) {
        return switch (applicationType) {
            case INSTANT -> true;
            case INFINITE -> this == HOLD || this == TOGGLE;
            case DURATION -> false;
        };
    }

    /// Whether the ability's lifetime is the Duration Effect's.
    public boolean endsWithDurationEffect() {
        return this == DURATION;
    }
}
