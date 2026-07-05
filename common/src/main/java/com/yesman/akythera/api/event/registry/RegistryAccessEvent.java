package com.yesman.akythera.api.event.registry;

import com.yesman.akythera.core.event.Event;
import net.minecraft.core.RegistryAccess;

/// Called on [RegistryAccess] created or destroyed
public abstract class RegistryAccessEvent extends Event {
    /// In a physical client, this even called when the player joins a world,
    /// receiving a frozen data registry packet from server.
    ///
    /// In a physical server, this event called only once the game launches
    public static final class Load extends RegistryAccessEvent {
        private final RegistryAccess registryAccess;

        public Load(RegistryAccess registryAccess) {
            this.registryAccess = registryAccess;
        }

        /// Returns a created registry access
        public final RegistryAccess getRegistryAccess() {
            return registryAccess;
        }
    }

    /// In physical client, this even called when the player quit a world
    ///
    /// In physical server, this event called only once the game terminated
    public static final class Unload extends RegistryAccessEvent {
    }
}
