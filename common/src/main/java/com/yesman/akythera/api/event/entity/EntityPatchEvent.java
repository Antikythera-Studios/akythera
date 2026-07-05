package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;

public abstract class EntityPatchEvent<T extends EntityPatch<?>> extends Event {
    private final T entityPatch;

    public EntityPatchEvent(final T entityPatch) {
        this.entityPatch = entityPatch;
    }

    public final T getEntityPatch() {
        return entityPatch;
    }
}
