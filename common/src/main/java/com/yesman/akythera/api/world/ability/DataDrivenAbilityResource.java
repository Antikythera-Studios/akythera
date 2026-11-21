package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import net.minecraft.core.Holder;

/// An [AbilityResourceScript] written in a data pack: its properties are the blueprint's values, and its functions run
/// the blueprint's graphs. What [AkytheraScriptFunctions#ABILITY_RESOURCE_CONSTRUCTOR] builds.
///
/// Shared by both logical sides, so a run holds the executor alone.
public final class DataDrivenAbilityResource implements AbilityResourceScript, DataDrivenScript<AbilityResourceScript, AbilityResourceBlueprint> {
    private final Holder.Reference<AbilityResourceBlueprint> blueprint;

    private final BlueprintExecutor executor;

    private final Kind kind;

    private final boolean canEmpty;

    public DataDrivenAbilityResource(final Holder.Reference<AbilityResourceBlueprint> blueprint) {
        BlueprintBody body = blueprint.value().body();

        this.blueprint = blueprint;
        this.kind = body.property(AbilityResourceScript.class, "kind", Kind.CONSUMPTIVE);
        this.canEmpty = body.property(AbilityResourceScript.class, "canEmpty", true);
        this.executor = blueprint.value().instantiate(this);
    }

    // ***************************************************************
    // AbilityResourceScript
    // ***************************************************************

    @Override
    public synchronized float read(final LivingEntityPatch<?> owner) {
        return executor.invokeMethod("read", null, owner) instanceof Number value ? value.floatValue() : 0.0F;
    }

    @Override
    public synchronized void write(final LivingEntityPatch<?> owner, final float value) {
        executor.invokeMethod("write", null, owner, value);
    }

    @Override
    public synchronized float clamp(final LivingEntityPatch<?> owner, final float value) {
        return executor.invokeMethod("clamp", null, owner, value) instanceof Number clamped ? clamped.floatValue() : value;
    }

    @Override
    public synchronized boolean appliesTo(final LivingEntityPatch<?> owner) {
        return Boolean.TRUE.equals(executor.invokeMethod("appliesTo", null, owner));
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public boolean canEmpty() {
        return canEmpty;
    }

    @Override
    public synchronized boolean hasResource(final LivingEntityPatch<?> owner, final float amount) {
        return Boolean.TRUE.equals(executor.invokeMethod(
            "hasResource", () -> AbilityResourceScript.super.hasResource(owner, amount), owner, amount));
    }

    @Override
    public synchronized boolean consume(final LivingEntityPatch<?> owner, final float amount) {
        return Boolean.TRUE.equals(executor.invokeMethod(
            "consume", () -> AbilityResourceScript.super.consume(owner, amount), owner, amount));
    }

    @Override
    public synchronized float add(final LivingEntityPatch<?> owner, final float amount) {
        return executor.invokeMethod("add", () -> AbilityResourceScript.super.add(owner, amount), owner, amount) instanceof Number delta
            ? delta.floatValue()
            : 0.0F;
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public AbilityResourceScript getScriptOwner() {
        return this;
    }

    @Override
    public Holder.Reference<AbilityResourceBlueprint> getBlueprint() {
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
