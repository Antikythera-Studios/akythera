package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.Entity;

/// An event triggered on both sides when an entity is removed from its level
public final class EntityRemoveEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final Entity.RemovalReason reason;

    public EntityRemoveEvent(final EntityPatch<?> entityPatch, final Entity.RemovalReason reason) {
        super(entityPatch);
        this.reason = reason;
    }

    public Entity.RemovalReason getReason() {
        return reason;
    }
}
