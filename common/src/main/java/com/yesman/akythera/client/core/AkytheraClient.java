package com.yesman.akythera.client.core;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.compat.IClientModCompatibility;
import com.yesman.akythera.client.gui.widget.BuiltInButtonTheme;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akythera.compat.MinecraftMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;

/// Common functionalities shared between the platform client entry points.
public final class AkytheraClient {
    private AkytheraClient() {
    }

    /// Called on each mod loader's mod entry point class's constructor
    public static void onModConstructed() {
        //InputAction.ENUM_MANAGER.registerEnumCls(Akythera.MODID, EpicFightInputAction.class);
        //InputAction.ENUM_MANAGER.registerEnumCls("minecraft", MinecraftInputAction.class);
        //WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, ColorDeterminator.Theme.class);

        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInButtonTheme.class);
    }

    /// Called on mod loader lifecycle when the mod initializes
    public static void onModInitialized() {
        ComponentsFactory.getImpl().setResourceManagerSupplier(Minecraft.getInstance()::getResourceManager);

        Arrays.stream(MinecraftMod.values())
            .filter(mod -> ModPlatformProvider.get().isModLoaded(mod.getModId()) && mod.getClientCompatibilityModule() != null)
            .map(mod -> {
                try {
                    return mod.getClientCompatibilityModule().getDeclaredConstructor().newInstance();
                } catch (
                    InstantiationException |
                    IllegalAccessException |
                    InvocationTargetException |
                    NoSuchMethodException e
                ) {
                    Akythera.LOGGER.error("Can't create an instance of client compatibility module {}. Skipped.", mod.getModId(), e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(IClientModCompatibility::onInitializeClient);

        //ComputeShaderProvider.checkIfSupports();
        //InputAction.ENUM_MANAGER.loadEnum();
        WidgetTheme.ENUM_MANAGER.loadEnum();
    }
}
