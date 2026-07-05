package com.yesman.akythera.neoforged.core;

import com.yesman.akythera.core.Akythera;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;

@Mod(value = Akythera.MODID, dist = Dist.DEDICATED_SERVER)
public final class AkytheraNeoForgedServer {
    public AkytheraNeoForgedServer(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::doServerStuff);
    }

    private void doServerStuff(final FMLDedicatedServerSetupEvent event) {
        Akythera.onModInitializedInDedicatedServer();
    }
}
