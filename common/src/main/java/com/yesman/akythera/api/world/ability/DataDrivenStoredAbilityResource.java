package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import net.minecraft.core.Holder;

/// A [StoredAbilityResource] written in a data pack: its settings are the blueprint's property values, and the
/// functions it leaves open run the blueprint's override when it declares one. What
/// [AkytheraScriptFunctions#STORED_ABILITY_RESOURCE_CONSTRUCTOR] builds.
///
/// Shared by both logical sides, so a run holds the executor alone.
public final class DataDrivenStoredAbilityResource extends StoredAbilityResource
    implements DataDrivenScript<AbilityResourceScript, AbilityResourceBlueprint>
{
    private final Holder.Reference<AbilityResourceBlueprint> blueprint;

    private final BlueprintExecutor executor;

    private final boolean canEmpty;

    public DataDrivenStoredAbilityResource(final Holder.Reference<AbilityResourceBlueprint> blueprint) {
        this(blueprint, blueprint.value().body());
    }

    private DataDrivenStoredAbilityResource(final Holder.Reference<AbilityResourceBlueprint> blueprint, final BlueprintBody body) {
        super(
            new AbilityResourceRef.ByData(blueprint.key()),
            body.property(StoredAbilityResource.class, "max", 0.0F),
            body.property(StoredAbilityResource.class, "regenPerTick", 0.0F),
            body.property(StoredAbilityResource.class, "regenDelay", 0),
            body.property(StoredAbilityResource.class, "kind", Kind.CONSUMPTIVE)
        );

        this.blueprint = blueprint;
        this.canEmpty = body.property(StoredAbilityResource.class, "canEmpty", true);
        this.executor = blueprint.value().instantiate(this);
    }

    // ***************************************************************
    // StoredAbilityResource
    // ***************************************************************

    @Override
    public boolean canEmpty() {
        return canEmpty;
    }

    @Override
    public synchronized boolean appliesTo(final LivingEntityPatch<?> owner) {
        return Boolean.TRUE.equals(executor.invokeMethod("appliesTo", () -> super.appliesTo(owner), owner));
    }

    @Override
    public synchronized boolean hasResource(final LivingEntityPatch<?> owner, final float amount) {
        return Boolean.TRUE.equals(executor.invokeMethod("hasResource", () -> super.hasResource(owner, amount), owner, amount));
    }

    @Override
    public synchronized boolean consume(final LivingEntityPatch<?> owner, final float amount) {
        return Boolean.TRUE.equals(executor.invokeMethod("consume", () -> super.consume(owner, amount), owner, amount));
    }

    @Override
    public synchronized float add(final LivingEntityPatch<?> owner, final float amount) {
        return executor.invokeMethod("add", () -> super.add(owner, amount), owner, amount) instanceof Number delta
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
