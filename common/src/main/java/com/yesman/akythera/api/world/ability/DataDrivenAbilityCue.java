package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/// An [AbilityCueScript] written in a data pack: its properties are the blueprint's values, and its events
/// run the blueprint's graphs.
public final class DataDrivenAbilityCue implements AbilityCueScript, DataDrivenScript<AbilityCue, AbilityCueBlueprint> {
    private final Holder.Reference<AbilityCueBlueprint> blueprint;

    private final AbilityCue owner;

    private final BlueprintExecutor executor;

    private final AbilityTagSet tags;

    private final int duration;

    private final boolean syncedToLateTrackers;

    public DataDrivenAbilityCue(final Holder.Reference<AbilityCueBlueprint> blueprint, final AbilityCue owner) {
        BlueprintBody body = blueprint.value().body();

        this.blueprint = blueprint;
        this.owner = owner;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(AbilityCueScript.class), body);
        this.tags = body.property(AbilityCueScript.class, "tags", AbilityTagSet.EMPTY);
        this.duration = body.property(AbilityCueScript.class, "duration", 0);
        this.syncedToLateTrackers = body.property(AbilityCueScript.class, "syncedToLateTrackers", false);
    }

    // ***************************************************************
    // AbilityCueScript
    // ***************************************************************

    @Override
    public AbilityTagSet getTags() {
        return tags;
    }

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public boolean isSyncedToLateTrackers() {
        return syncedToLateTrackers;
    }

    @Override
    public void onActivate(final AbilityCue cue, final @Nullable AbilityState state) {
        executor.invokeMethod("onActivate", null, cue, state);
    }

    @Override
    public void onTick(final AbilityCue cue) {
        executor.invokeMethod("onTick", null, cue);
    }

    @Override
    public void onCanceled(final AbilityCue cue, final @Nullable AbilityState state) {
        executor.invokeMethod("onCanceled", null, cue, state);
    }

    @Override
    public void onEnd(final AbilityCue cue, final @Nullable AbilityState state) {
        executor.invokeMethod("onEnd", null, cue, state);
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public AbilityCue getScriptOwner() {
        return owner;
    }

    @Override
    public Holder.Reference<AbilityCueBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return executor.variables();
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
