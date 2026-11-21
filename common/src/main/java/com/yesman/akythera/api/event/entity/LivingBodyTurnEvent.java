package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.LivingEntity;

/// Posted after the vanilla body turn of [LivingEntity#tick]. Listeners may set [LivingEntity#yBodyRot].
public final class LivingBodyTurnEvent extends LivingEntityPatchEvent {
    public LivingBodyTurnEvent(final EntityPatch<LivingEntity> entityPatch) {
        super(entityPatch);
    }
}
