package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenAbilityState;
import net.minecraft.core.Holder;

/// A [SystematicAbility] whose event entry points run the blueprint's graphs after the Java ones, and whose
/// `canActivate` runs the blueprint's override when it declares one, so an ability needs no class in the
/// codebase.
///
/// What [AkytheraScriptFunctions#SYSTEMATIC_ABILITY_CONSTRUCTOR] builds for a [SystematicAbility] written in
/// a data pack. It is never a parent of its own.
public class DataDrivenAbility extends SystematicAbility<DataDrivenAbilityState> {
    private final Holder.Reference<AbilityBlueprint> blueprint;

    public DataDrivenAbility(final Holder.Reference<AbilityBlueprint> blueprint) {
        super(blueprint);

        this.blueprint = blueprint;
    }

    @Override
    public DataDrivenAbilityState createState() {
        return blueprint.value().instantiate(this);
    }

    /// Refuses once the activation graph has thrown, so the ability stops charging costs and
    /// spending charges for a graph that no longer runs.
    ///
    /// Fails closed: an override that throws, or answers with anything but true, refuses the activation.
    @Override
    public boolean canActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        if (abilityState.getBlueprintExecutor().hasFailed("onActivated")) return false;

        return Boolean.TRUE.equals(abilityState.getBlueprintExecutor().invokeMethod(
            "canActivate",
            () -> super.canActivate(abilitySystemComponent, abilityInstance, abilityState),
            abilitySystemComponent, abilityInstance, abilityState
        ));
    }

    @Override
    public void onEquipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onEquipped(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onEquipped", null, abilitySystemComponent, abilityInstance, abilityState);
    }

    @Override
    public void onUnequipped(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onUnequipped(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onUnequipped", null, abilitySystemComponent, abilityInstance, abilityState);
    }

    @Override
    public void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onActivated(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onActivated", null, abilitySystemComponent, abilityInstance, abilityState);
    }

    @Override
    public void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onRecast(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onRecast", null, abilitySystemComponent, abilityInstance, abilityState);
    }

    @Override
    public void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onCanceled(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onCanceled", null, abilitySystemComponent, abilityInstance, abilityState);
    }

    @Override
    public void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final DataDrivenAbilityState abilityState
    ) {
        super.onFinished(abilitySystemComponent, abilityInstance, abilityState);

        abilityState.getBlueprintExecutor().invokeMethod(
            "onFinished", null, abilitySystemComponent, abilityInstance, abilityState);
    }
}
