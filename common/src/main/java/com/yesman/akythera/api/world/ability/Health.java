package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import net.minecraft.world.entity.LivingEntity;

/// An entity's health, bounded by its max_health attribute and written through [LivingEntity#setHealth].
public final class Health implements AbilityResourceScript {
    @Override
    public float read(final LivingEntityPatch<?> owner) {
        return owner.getOriginal().getHealth();
    }

    @Override
    public void write(final LivingEntityPatch<?> owner, final float value) {
        owner.getOriginal().setHealth(value);
    }

    @Override
    public float clamp(final LivingEntityPatch<?> owner, final float value) {
        return Math.clamp(value, 0.0F, owner.getOriginal().getMaxHealth());
    }

    @Override
    public boolean appliesTo(final LivingEntityPatch<?> owner) {
        return true;
    }

    /// Zero health is death.
    @Override
    public boolean canEmpty() {
        return false;
    }
}
