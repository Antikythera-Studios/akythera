package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.LivingEntity;

/// Events around the [LivingEntity#travel] call of [LivingEntity#aiStep], on the side that moves the entity.
public abstract class LivingTravelEvent extends LivingEntityPatchEvent {
    public LivingTravelEvent(final EntityPatch<LivingEntity> entityPatch) {
        super(entityPatch);
    }

    /// Posted after the jump input is handled, before the travel. Listeners may change the movement input and speed.
    public static final class Pre extends LivingTravelEvent {
        public Pre(final EntityPatch<LivingEntity> entityPatch) {
            super(entityPatch);
        }
    }

    /// Posted right after the travel.
    public static final class Post extends LivingTravelEvent {
        public Post(final EntityPatch<LivingEntity> entityPatch) {
            super(entityPatch);
        }
    }
}
