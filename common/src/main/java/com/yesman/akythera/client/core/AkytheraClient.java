package com.yesman.akythera.client.core;

import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.compat.IClientModPlugin;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.impl.gl.GlComputeBackend;
import com.yesman.akythera.client.core.shader.backend.impl.vulkan.VulkanComputeBackend;
import com.yesman.akythera.client.core.shader.backend.setup.VanillaAnimationComputeShader;
import com.yesman.akythera.client.gui.widget.BuiltInButtonTheme;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.platform.ModPlatformProvider;

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
        Arrays.stream(CompatibleMod.values())
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
            .forEach(IClientModPlugin::onInitializeClient);

        //InputAction.ENUM_MANAGER.loadEnum();
        WidgetTheme.ENUM_MANAGER.loadEnum();
    }

    /// Called once the gpu device is created and the OpenGL context is current.
    /// Both loaders run their client entry points before this point, so no OpenGL
    /// state can be queried in [#onModInitialized()].
    public static void onRendererInitialized() {
        ClientComponentsFactory clientComponentsFactory = ClientComponentsFactory.getImpl();
        clientComponentsFactory.inspectDeviceSpec();

        if (!clientComponentsFactory.supportComputeShader()) {
            return;
        }

        ComputeBackends.install(switch (clientComponentsFactory.getGraphicsApi()) {
            case OPENGL -> new GlComputeBackend();
            case VULKAN -> VulkanComputeBackend.create();
            // ComponentsFactory#inspectDeviceSpec reports no compute support for an API it does not
            // know, so the early return above has already been taken.
            case UNKNOWN -> throw new IllegalStateException("Compute shader support was claimed for an unrecognized graphics backend");
        });

        if (clientComponentsFactory.getShaderBackend() == null) {
            // Initialize vanilla shader preemptively, overwritten if any modded shader backen installed.
            clientComponentsFactory.initComputeShaderFactory(
                ClientComponentsFactory.ShaderBackend.VANILLA,
                VanillaAnimationComputeShader::new,
                VanillaAnimationComputeShader::invalidateProgram
            );
        }
    }
}
