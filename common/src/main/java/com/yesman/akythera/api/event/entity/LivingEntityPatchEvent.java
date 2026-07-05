package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityEventListener;
import net.minecraft.world.entity.LivingEntity;

/// Events that are called during an entity's lifecycle
/// 
/// Developers can add or remove these events through [EntityEventListener] in runtime
public abstract class LivingEntityPatchEvent extends EntityPatchEvent<EntityPatch<LivingEntity>> {
    public LivingEntityPatchEvent(EntityPatch<LivingEntity> entityPatch) {
        super(entityPatch);
    }
}