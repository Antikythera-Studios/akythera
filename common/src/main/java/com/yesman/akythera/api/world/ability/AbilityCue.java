package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/// One live play of client side logic with a lifetime, such as a sound that fades or a particle emitter
/// that follows the owner. A one-shot sound or particle belongs in the ability's own logic instead.
///
/// What the play does is its [AbilityCueScript]. The server starts and ends every play and tells the
/// clients; it keeps a play for its timer, its tags and its ability. The events run on each client holding
/// the play.
@ScriptType
public final class AbilityCue {
    /// Names the play on the wire, unique within its owner.
    private final int id;

    private final AbilitySystemComponent owner;

    /// The ability that played this. Null for a cue played outside any ability.
    private final @Nullable AbilityInstance source;

    /// Whether the script reads [#source]'s state.
    private final boolean withState;

    /// Whether the play is canceled when the ability that started it ends.
    private final boolean endsWithAbility;

    private final AbilityCueScript script;

    /// Ticks this play has run.
    private int elapsedTicks;

    private boolean valid = true;

    public AbilityCue(
        final int id,
        final AbilitySystemComponent owner,
        final @Nullable AbilityInstance source,
        final boolean withState,
        final boolean endsWithAbility,
        final Function<AbilityCue, AbilityCueScript> scriptProvider
    ) {
        this.id = id;
        this.owner = owner;
        this.source = source;
        this.withState = withState;
        this.endsWithAbility = endsWithAbility;
        this.script = scriptProvider.apply(this);
    }

    public int getId() {
        return id;
    }

    @ScriptFunction(pure = true, label = "Get Ability System Component", category = "Ability/Cue")
    public AbilitySystemComponent getOwner() {
        return owner;
    }

    @ScriptFunction(pure = true, label = "Get Cue Source", category = "Ability/Cue")
    public @Nullable AbilityInstance getSource() {
        return source;
    }

    /// [#source]'s state when the play reads it, else null.
    @ScriptFunction(pure = true, label = "Get Cue Source State", category = "Ability/Cue")
    public @Nullable AbilityState getSourceState() {
        return withState && source != null ? source.getState() : null;
    }

    /// A copy of [#source]'s state as it is now when the play reads it, else null.
    public @Nullable AbilityState snapshotSourceState() {
        Ability<?> ability = source == null ? null : source.getAbility();

        if (!withState || ability == null) return null;

        AbilityState snapshot = ability.createState();

        snapshot.copyState(source.getState());

        return snapshot;
    }

    public boolean withState() {
        return withState;
    }

    public boolean endsWithAbility() {
        return endsWithAbility;
    }

    public AbilityCueScript getScript() {
        return script;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    /// Ticks left, or -1 while the cue has no timer.
    @ScriptFunction(pure = true, label = "Get Remaining Ticks", category = "Ability/Cue")
    public int getRemainingTicks() {
        int duration = script.getDuration();

        return duration > 0 ? Math.max(duration - elapsedTicks, 0) : -1;
    }

    /// Whether this play is still running.
    @ScriptFunction(pure = true, label = "Is Cue Valid", category = "Ability/Cue")
    public boolean isValid() {
        return valid;
    }

    /// Whether any of the script's tags matches `query`, by the path-prefix match tags use everywhere.
    public boolean hasTag(final AbilityTag query) {
        return script.getTags().hasTag(query);
    }

    /// Counts the timer up one tick. True once the cue has run out.
    public boolean tickDuration() {
        int duration = script.getDuration();

        if (duration <= 0) return false;

        if (elapsedTicks < duration) elapsedTicks++;

        return elapsedTicks >= duration;
    }

    @ApiStatus.Internal
    void invalidate() {
        valid = false;
    }

    /// Resumes a play the server started `elapsedTicks` ago.
    @ApiStatus.Internal
    void setElapsedTicks(final int elapsedTicks) {
        this.elapsedTicks = elapsedTicks;
    }
}
