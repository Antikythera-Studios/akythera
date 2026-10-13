package com.yesman.akythera.neoforged.core;

import com.yesman.akythera.client.config.AkytheraClientConfig;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.neoforged.core.networking.NeoForgedNetworkingHelper;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akythera.platform.neoforged.NeoForgedModPlatform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Akythera.MODID)
public class AkytheraNeoForged {
    public AkytheraNeoForged(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgedModPlatform neoForgedModPlatform = (NeoForgedModPlatform) ModPlatformProvider.get();
        Akythera.onModConstructed();
        Akythera.registerBuiltInDataBlockClasses();

        modContainer.registerConfig(ModConfig.Type.CLIENT, AkytheraClientConfig.SPEC);

        // Register mod bus events
		modEventBus.addListener(this::doCommonStuff);
        modEventBus.addListener(NeoForgedNetworkingHelper::register);
        neoForgedModPlatform.addListeners(modEventBus);

        // Register game bus events
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
	}

	private void doCommonStuff(final FMLCommonSetupEvent event) {
        NetworkingHelper.initialize(new NeoForgedNetworkingHelper());
        Akythera.onModInitialized();
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        Akythera.onRegisterCommands(event.getDispatcher());
    }
}