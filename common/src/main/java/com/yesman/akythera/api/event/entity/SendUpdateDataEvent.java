package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.EntityType;

/// Called on syncing server side data to clients, in each interval tick
/// defined per entity type. ([EntityType#updateInterval])
///
/// @see ServerEntity#sendChanges()
public final class SendUpdateDataEvent extends EntityPatchEvent<EntityPatch<?>> {
    public SendUpdateDataEvent(EntityPatch<?> entityPatch) {
        super(entityPatch);
    }
}
