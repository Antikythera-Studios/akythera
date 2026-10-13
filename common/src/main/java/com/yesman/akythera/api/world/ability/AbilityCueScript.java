package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
/// The logic of an [AbilityCue]: its settings and its events. A Java class registered as a [ScriptType] in
/// [AkytheraRegistries.Frozen#SCRIPT_TYPE], or a data pack entry run as a [DataDrivenAbilityCue].
public interface AbilityCueScript {
    /// The tags [AbilitySystemComponent#cancelCue] matches.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    default AbilityTagSet getTags() {
        return AbilityTagSet.EMPTY;
    }

    /// Ticks the cue runs for. Zero or less runs until something cancels it.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    default int getDuration() {
        return 0;
    }

    /// Whether a player who starts tracking the owner mid-play is sent the play.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    default boolean isSyncedToLateTrackers() {
        return false;
    }

    /// Called when the server's play starts on a client.
    ///
    /// @param state a copy of the source ability's state when the server played the cue, or null when the
    ///              play does not read it
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onActivate(final AbilityCue cue, final @Nullable AbilityState state);

    /// Called every client tick while the play runs.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onTick(final AbilityCue cue);

    /// Called when the server cancels the play.
    ///
    /// @param state a copy of the source ability's state when the server canceled the play, or null when
    ///              the play does not read it
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onCanceled(final AbilityCue cue, final @Nullable AbilityState state);

    /// Called when the play's timer runs out on the server.
    ///
    /// @param state a copy of the source ability's state when the server ended the play, or null when the
    ///              play does not read it
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onEnd(final AbilityCue cue, final @Nullable AbilityState state);

    /// Whether `type` can be played as a Java cue: an [AbilityCueScript] declaring a public no-argument
    /// constructor.
    static boolean runnable(final Class<?> type) {
        if (!AbilityCueScript.class.isAssignableFrom(type)) return false;

        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /// A new `type` through its public no-argument constructor.
    ///
    /// @throws IllegalStateException when the constructor is missing or throws
    static AbilityCueScript instantiate(final Class<? extends AbilityCueScript> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed at building ability cue script " + type.getSimpleName(), e);
        }
    }
}
