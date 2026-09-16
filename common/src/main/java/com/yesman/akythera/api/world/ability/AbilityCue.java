package com.yesman.akythera.api.world.ability;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/// Client side logic with a lifetime: a sound that fades, a particle emitter that follows the
/// owner. A one-shot sound or particle belongs in the ability's own logic instead.
///
/// The behavior is the editor's `onExecute` graph. Java authors pass a [Behavior] until that lands.
public final class AbilityCue {
    private final Identifier id;

    /// Matched by [AbilitySystemComponent#cancelCue].
    private final Set<AbilityTag> tags;

    /// Seeds [AbilityCueInstance#endsWithAbility], which each play may override.
    private final boolean endsWithAbility;

    /// Ticks the cue runs for. Zero or less runs until something cancels it.
    private final int duration;

    private final Behavior behavior;

    public AbilityCue(
        final Identifier id,
        final Set<AbilityTag> tags,
        final boolean endsWithAbility,
        final int duration,
        final Behavior behavior
    ) {
        this.id = id;
        this.tags = Set.copyOf(tags);
        this.endsWithAbility = endsWithAbility;
        this.duration = duration;
        this.behavior = behavior;
    }

    public Identifier getId() {
        return id;
    }

    public Set<AbilityTag> getTags() {
        return tags;
    }

    public boolean endsWithAbility() {
        return endsWithAbility;
    }

    public int getDuration() {
        return duration;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    /// Whether any of this cue's tags matches `query`, by the path-prefix match tags use everywhere.
    public boolean hasTag(final AbilityTag query) {
        for (AbilityTag tag : tags) {
            if (query.matches(tag)) return true;
        }

        return false;
    }

    /// What one cue does. The `state` is the snapshot the server sent with the activation, and is
    /// null when the cue was played without one.
    public interface Behavior {
        void onExecute(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance,
            final @Nullable AbilityState abilityState
        );

        default void tick(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance
        ) {

        }

        default void onEnd(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance
        ) {

        }

        default void onCanceled(
            final AbilitySystemComponent abilitySystemComponent,
            final AbilityCueInstance cueInstance
        ) {

        }
    }
}
