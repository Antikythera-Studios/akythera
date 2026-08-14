package com.yesman.akythera.api.lifecycle;

import com.yesman.akythera.core.asset.DedicatedServerAssetResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/// *Physical server*(a.k.a. Dedicated server) side components factory. In this class,
/// there shouldn't be any client side code including effective calls of them.
public final class ServerComponentsFactory extends ComponentsFactory {
    /// Returns server component factory by force casting
    public static ServerComponentsFactory getImpl() {
        return (ServerComponentsFactory)ComponentsFactory.getImpl();
    }

    /// Reads assets out of the data tree. Also a data pack reload listener, registered in
    /// [com.yesman.akythera.core.Akythera#onModInitializedInDedicatedServer].
    private final DedicatedServerAssetResourceManager resourceManager = new DedicatedServerAssetResourceManager();

    @Override
    public DedicatedServerAssetResourceManager getResourceManager() {
        return resourceManager;
    }

    @Override
    public @Nullable PlayerSkin getPlayerSkin(final Player player) {
        return null;
    }
}
