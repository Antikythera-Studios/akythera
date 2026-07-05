package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.server.level.ServerPlayer;

/// An event triggered when a player starts or stop being tracking the entity
public abstract class EntityTrackingEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final ServerPlayer tracker;

    public EntityTrackingEvent(EntityPatch<?> entityPatch, ServerPlayer tracker) {
        super(entityPatch);
        this.tracker = tracker;
    }

    /// Returns a player who is tracking now
    public final ServerPlayer getTracker() {
        return tracker;
    }

    /// On tracking started
    public static final class Start extends EntityTrackingEvent {
        public Start(EntityPatch<?> entityPatch, ServerPlayer tracker) {
            super(entityPatch, tracker);
        }
    }

    /// On tracking stopped
    public static final class Stop extends EntityTrackingEvent {
        public Stop(EntityPatch<?> entityPatch, ServerPlayer tracker) {
            super(entityPatch, tracker);
        }
    }
}
