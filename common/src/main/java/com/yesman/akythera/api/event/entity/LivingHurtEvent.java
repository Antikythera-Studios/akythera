package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/// An event triggered on the server when a living entity that can take `damageSource` is hit, before armor,
/// shields and invulnerability ticks. Listeners may change the damage.
public final class LivingHurtEvent extends LivingEntityPatchEvent {
    private final DamageSource damageSource;

    /// The damage the hit deals, in half hearts.
    private float amount;

    public LivingHurtEvent(final EntityPatch<LivingEntity> entityPatch, final DamageSource damageSource, final float amount) {
        super(entityPatch);
        this.damageSource = damageSource;
        this.amount = amount;
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(final float amount) {
        this.amount = amount;
    }
}
