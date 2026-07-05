package com.yesman.akythera.core.networking;

import net.minecraft.world.entity.player.Player;

/// An inter-modloader payload context handler tagged when
/// receiving packets on each side.
public interface PayloadContext {
    /// Returns a receiving player
    Player receiver();

    static PayloadContext createContext(Player receiver) {
        return new PayloadContext.Impl(receiver);
    }

    record Impl(Player receiver) implements PayloadContext {
    }
}
