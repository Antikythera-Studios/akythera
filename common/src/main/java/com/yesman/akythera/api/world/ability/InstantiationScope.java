package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptType;

/// Which sides an [Ability] exists on.
@ScriptType(autoRegister = false)
public enum InstantiationScope {
    /// Nothing is sent. No [AbilityInstance] exists on any client.
    SERVER_ONLY,

    /// Sent to the owner's client only. A remote player tracking the owner never instantiates it.
    LOCAL_AND_SERVER,

    /// Sent to every tracking player.
    EVERYWHERE;

    /// Whether the owner's own client instantiates the ability.
    public boolean reachesOwner() {
        return this != SERVER_ONLY;
    }

    /// Whether players tracking the owner instantiate it.
    public boolean reachesTrackers() {
        return this == EVERYWHERE;
    }
}
