package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;

public abstract class TickEvent extends EntityPatchEvent<EntityPatch<?>> {
    public TickEvent(final EntityPatch<?> entityPatch) {
        super(entityPatch);
    }

    /// Called on logical client pre-tick
    public static final class ClientPre extends TickEvent {
        public ClientPre(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// Called on logical client pre-tick
    public static final class ClientPost extends TickEvent {
        public ClientPost(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// Called on variable frame (render tick) in client side.
    public static final class RenderTick extends TickEvent {
        private final float deltaTime;
        private final float partialTick;

        public RenderTick(final EntityPatch<?> entityPatch, final float deltaTime, final float partialTick) {
            super(entityPatch);

            this.deltaTime = deltaTime;
            this.partialTick = partialTick;
        }

        /// Time dilation from the last frame to the current frame
        public float getDeltaTime() {
            return deltaTime;
        }

        public float getPartialTick() {
            return partialTick;
        }
    }

    /// Called on logical server pre-tick
    public static final class ServerPre extends TickEvent {
        public ServerPre(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// Called on logical server pre-tick
    public static final class ServerPost extends TickEvent {
        public ServerPost(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// Called on common side pre-tick
    public static final class CommonPre extends TickEvent {
        public CommonPre(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }

    /// Called on common side pre-tick
    public static final class CommonPost extends TickEvent {
        public CommonPost(final EntityPatch<?> entityPatch) {
            super(entityPatch);
        }
    }
}
