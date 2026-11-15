package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;

/// An [AbilityResource] the owner's [AbilitySystemComponent] holds, from 0 to [#max]: stamina, mana. It refills by
/// [#regenPerTick] once [#regenDelay] ticks passed since it last went down. An owner starts full.
///
/// The server's value is the real one. A player's own client mirrors it and refills it alongside.
public class StoredAbilityResource implements AbilityResource {
    private final float max;

    /// What it refills by every tick.
    private final float regenPerTick;

    /// The ticks it waits after going down before it refills.
    private final int regenDelay;

    private final Kind kind;

    public StoredAbilityResource(final float max, final float regenPerTick, final int regenDelay, final Kind kind) {
        this.max = max;
        this.regenPerTick = regenPerTick;
        this.regenDelay = regenDelay;
        this.kind = kind;
    }

    public float max() {
        return max;
    }

    public float regenPerTick() {
        return regenPerTick;
    }

    public int regenDelay() {
        return regenDelay;
    }

    @Override
    public float read(final LivingEntityPatch<?> owner) {
        AbilitySystemComponent abilitySystem = owner.getComponent(AbilitySystemComponent.class);

        return abilitySystem == null ? 0.0F : abilitySystem.getStoredResource(this);
    }

    @Override
    public void write(final LivingEntityPatch<?> owner, final float value) {
        AbilitySystemComponent abilitySystem = owner.getComponent(AbilitySystemComponent.class);

        if (abilitySystem != null) abilitySystem.setStoredResource(this, value);
    }

    @Override
    public float clamp(final LivingEntityPatch<?> owner, final float value) {
        return Math.clamp(value, 0.0F, max);
    }

    @Override
    public boolean appliesTo(final LivingEntityPatch<?> owner) {
        return owner.getComponent(AbilitySystemComponent.class) != null;
    }

    @Override
    public Kind kind() {
        return kind;
    }
}
