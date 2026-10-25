package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/// An event triggered on the server when a living entity starts dying
public final class LivingDeathEvent extends LivingEntityPatchEvent {
    private final @Nullable DamageSource damageSource;

    public LivingDeathEvent(final EntityPatch<LivingEntity> entityPatch, final @Nullable DamageSource damageSource) {
        super(entityPatch);
        this.damageSource = damageSource;
    }

    /// Returns the last damage the entity took, or null when it took none recently
    public @Nullable DamageSource getDamageSource() {
        return damageSource;
    }
}
