package com.yesman.akythera.client.core;

import com.yesman.akythera.client.api.gui.BuiltInButtonTheme;
import com.yesman.akythera.client.api.gui.BuiltInCheckBoxTheme;
import com.yesman.akythera.client.api.gui.BuiltInComboBoxTheme;
import com.yesman.akythera.client.api.gui.BuiltInDragValueBoxTheme;
import com.yesman.akythera.client.api.gui.BuiltInEditBoxTheme;
import com.yesman.akythera.client.api.gui.HudManager;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.compat.IClientModPlugin;
import com.yesman.akythera.client.core.physics.cloth.ClothColliderPacker;
import com.yesman.akythera.client.core.renderer.collider.ColliderRenderers;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.impl.gl.GlComputeBackend;
import com.yesman.akythera.client.core.shader.backend.impl.vulkan.VulkanComputeBackend;
import com.yesman.akythera.client.core.shader.backend.setup.VanillaAnimationComputeShader;
import com.yesman.akythera.client.input.AbilityInputBinder;
import com.yesman.akythera.client.input.AkytheraKeyMappings;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.platform.ModPlatformProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.client.api.gui.script.HUDScript;
import com.yesman.akythera.client.api.gui.script.ScreenScript;
import com.yesman.akythera.client.api.gui.script.WidgetScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;

/// Common functionalities shared between the platform client entry points.
public final class AkytheraClient {
    private AkytheraClient() {
    }

    /// Called on each mod loader's mod entry point class's constructor
    public static void onModConstructed() {
        //WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, ColorDeterminator.Theme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInButtonTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInCheckBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInComboBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInEditBoxTheme.class);
        WidgetTheme.ENUM_MANAGER.registerEnumCls(Akythera.MODID, BuiltInDragValueBoxTheme.class);
        AkytheraKeyMappings.bootStrap();
        AbilityInputBinder.registerEvents();
        HudManager.registerEvents();

        ScriptModularizeHelper.registerBlueprintLayout(AkytheraScreen.class, ScreenScript.class);
        ScriptModularizeHelper.registerBlueprintLayout(AkytheraWidget.class, WidgetScript.class);
        ScriptModularizeHelper.registerBlueprintLayout(AkytheraHUD.class, HUDScript.class);
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

        WidgetTheme.ENUM_MANAGER.loadEnum();

        // Renderers key off the collider type constants, so they bind after the enum loads.
        ColliderRenderers.registerBuiltInRenderers();

        ClientComponentsFactory clientComponentsFactory = ClientComponentsFactory.getImpl();
        clientComponentsFactory.registerClothColliderPacker(AkytheraColliderType.SPHERE, ClothColliderPacker.SPHERE);
        clientComponentsFactory.registerClothColliderPacker(AkytheraColliderType.CAPSULE, ClothColliderPacker.CAPSULE);
        clientComponentsFactory.registerClothColliderPacker(AkytheraColliderType.ORIENTED_BOX, ClothColliderPacker.ORIENTED_BOX);
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
