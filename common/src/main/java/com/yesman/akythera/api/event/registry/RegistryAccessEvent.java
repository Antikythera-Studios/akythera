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

    /// Called when the data pack registries of a loaded [RegistryAccess] are refilled in place, without a world
    /// reload. Anything built from their entries is stale.
    public static final class Reload extends RegistryAccessEvent {
        private final RegistryAccess registryAccess;

        public Reload(RegistryAccess registryAccess) {
            this.registryAccess = registryAccess;
        }

        /// Returns the refilled registry access
        public final RegistryAccess getRegistryAccess() {
            return registryAccess;
        }
    }
}
