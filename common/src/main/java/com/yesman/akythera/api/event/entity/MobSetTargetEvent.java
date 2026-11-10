package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/// An event triggered on the server when [Mob#setTarget] changes the mob's target
public final class MobSetTargetEvent extends LivingEntityPatchEvent {
    private final @Nullable LivingEntity target;

    public MobSetTargetEvent(final EntityPatch<LivingEntity> entityPatch, final @Nullable LivingEntity target) {
        super(entityPatch);
        this.target = target;
    }

    /// Returns the new target, or null when the target is cleared
    public @Nullable LivingEntity getTarget() {
        return target;
    }
}
