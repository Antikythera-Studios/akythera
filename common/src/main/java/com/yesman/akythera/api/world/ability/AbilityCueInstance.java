package com.yesman.akythera.api.world.ability;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// One live play of an [AbilityCue], parked on the [AbilityInstance] that started it, on the client
/// too.
public final class AbilityCueInstance {
    private final AbilityCue cue;

    /// The ability that played this. Null for a cue played outside any ability.
    private final @Nullable AbilityInstance source;

    /// Seeded from [AbilityCue#endsWithAbility], overridable per play.
    private final boolean endsWithAbility;

    /// Ticks left, or less than zero while the cue has no timer.
    private int remainingTicks;

    private boolean valid = true;

    public AbilityCueInstance(
        final AbilityCue cue,
        final @Nullable AbilityInstance source,
        final boolean endsWithAbility
    ) {
        this.cue = cue;
        this.source = source;
        this.endsWithAbility = endsWithAbility;
        this.remainingTicks = cue.getDuration() > 0 ? cue.getDuration() : -1;
    }

    public AbilityCue getCue() {
        return cue;
    }

    public @Nullable AbilityInstance getSource() {
        return source;
    }

    public boolean endsWithAbility() {
        return endsWithAbility;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    /// Whether this play is still running.
    public boolean isValid() {
        return valid;
    }

    /// Counts the timer down one tick. True once the cue has run out.
    public boolean tickDuration() {
        if (remainingTicks < 0) return false;

        if (remainingTicks > 0) remainingTicks--;

        return remainingTicks <= 0;
    }

    @ApiStatus.Internal
    void invalidate() {
        valid = false;
    }
}
