package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import org.jspecify.annotations.Nullable;

/// An [AbilityResourceScript] an owner's [AbilitySystemComponent] holds, from 0 to [#getMax]: stamina, mana. It
/// refills by [#getRegenPerTick] once [#getRegenDelay] ticks passed since it last went down. An owner starts full.
///
/// The server's value is the real one, and a player's own client mirrors it and refills it alongside.
public class StoredAbilityResource implements AbilityResourceScript {
    /// What the owner's [AbilitySystemComponent] stores the value under.
    private final AbilityResourceRef ref;

    private final float max;

    /// What it refills by every tick.
    private final float regenPerTick;

    /// The ticks it waits after going down before it refills.
    private final int regenDelay;

    private final Kind kind;

    /// A subclass registered as a [ScriptType], stored under its class. It overrides the getters of the settings it
    /// sets; the others are 0 and [Kind#CONSUMPTIVE].
    ///
    /// @throws IllegalStateException when the class is no registered [ScriptType]
    protected StoredAbilityResource() {
        ScriptType type = ScriptType.of(getClass());

        if (type == null) throw new IllegalStateException(getClass().getSimpleName() + " is no registered ScriptType");

        this.ref = new AbilityResourceRef.ByClass(type);
        this.max = 0.0F;
        this.regenPerTick = 0.0F;
        this.regenDelay = 0;
        this.kind = Kind.CONSUMPTIVE;
    }

    protected StoredAbilityResource(
        final AbilityResourceRef ref, final float max, final float regenPerTick, final int regenDelay, final Kind kind
    ) {
        this.ref = ref;
        this.max = max;
        this.regenPerTick = regenPerTick;
        this.regenDelay = regenDelay;
        this.kind = kind;
    }

    /// The stored resource `ref` names, or null when it names none or another resource.
    public static @Nullable StoredAbilityResource get(final AbilityResourceRef ref) {
        return ComponentsFactory.getImpl().getAbilityResource(ref) instanceof StoredAbilityResource stored ? stored : null;
    }

    public AbilityResourceRef getRef() {
        return ref;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public float getMax() {
        return max;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public float getRegenPerTick() {
        return regenPerTick;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    public int getRegenDelay() {
        return regenDelay;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public final float read(final LivingEntityPatch<?> owner) {
        AbilitySystemComponent abilitySystem = owner.getComponent(AbilitySystemComponent.class);

        return abilitySystem == null ? 0.0F : abilitySystem.getStoredResource(this);
    }

    @Override
    public final void write(final LivingEntityPatch<?> owner, final float value) {
        AbilitySystemComponent abilitySystem = owner.getComponent(AbilitySystemComponent.class);

        if (abilitySystem != null) abilitySystem.setStoredResource(this, value);
    }

    @Override
    public final float clamp(final LivingEntityPatch<?> owner, final float value) {
        return Math.clamp(value, 0.0F, getMax());
    }

    /// Whether `owner` has an [AbilitySystemComponent] to hold the value.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    @Override
    public boolean appliesTo(final LivingEntityPatch<?> owner) {
        return owner.getComponent(AbilitySystemComponent.class) != null;
    }
}
