package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.api.input.TriggeringType;
import com.yesman.akythera.core.reflection.ScriptType;

/// A [SystematicAbility] preset wiring an input action's [TriggeringState]s to the ability
/// lifecycle. A bare [Ability] has no activation type.
@ScriptType(autoRegister = false)
public enum ActivationType {
    /// Activates, then ends on the same tick.
    INSTANT,

    /// Activates, then finishes when the Duration Effect expires.
    DURATION,

    /// The first trigger activates and starts the hold count; the release finishes it, or cancels
    /// it when the count fell below the input action's threshold.
    HOLD,

    /// The first trigger activates, the second cancels.
    TOGGLE;

    /// Whether `triggeringType` may drive this preset.
    public boolean accepts(final TriggeringType triggeringType) {
        return switch (this) {
            case INSTANT, DURATION -> true;
            case HOLD -> triggeringType.isHeld();
            case TOGGLE -> triggeringType == TriggeringType.PRESS;
        };
    }

    /// Whether the client sends a trigger packet for this event.
    ///
    /// @param alreadyActivated whether the instance was already running when the event fired
    public boolean emitsTriggerPacket(
        final TriggeringState triggeringState,
        final boolean alreadyActivated
    ) {
        return switch (this) {
            case INSTANT, DURATION, TOGGLE -> triggeringState == TriggeringState.TRIGGERED;
            case HOLD -> switch (triggeringState) {
                case TRIGGERED -> !alreadyActivated;
                case COMPLETED, CANCELED -> true;
            };
        };
    }

    /// Whether a second activation request on a running ability cancels it rather than recasting.
    public boolean recastCancels() {
        return this == TOGGLE;
    }

    /// Whether a second activation request on a running ability finishes it rather than recasting.
    ///
    /// A Hold preset sends exactly two packets, and the second one is the release.
    public boolean recastFinishes() {
        return this == HOLD;
    }

    /// Whether this preset is unusable without a wired input action.
    public boolean requiresInputAction() {
        return this == HOLD || this == TOGGLE;
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
