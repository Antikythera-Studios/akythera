package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.client.Camera;

/// An event triggered on the client when the entity becomes or stops being [Camera#entity]
public abstract class EntityPossessionEvent extends EntityPatchEvent<EntityPatch<?>> {
    public EntityPossessionEvent(final EntityPatch<?> entityPatch) {
        super(entityPatch);
    }

    /// The camera started following the entity
    public static final class Possessed extends EntityPossessionEvent {
        public Possessed(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// The camera stopped following the entity
    public static final class Unpossessed extends EntityPossessionEvent {
        public Unpossessed(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }
}
