package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.client.core.AkytheraClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The first point of the client lifecycle where the OpenGL context is current and its capabilities
/// are created (see `GlDevice.<init>`), which both mod loaders' client entry points run before.
@Mixin(RenderSystem.class)
public abstract class MixinRenderSystem {
    @Inject(at = @At("TAIL"), method = "initRenderer(Lcom/mojang/blaze3d/systems/GpuDevice;)V")
    private static void akythera$inspectDeviceSpec(GpuDevice device, CallbackInfo callbackInfo) {
        AkytheraClient.onRendererInitialized();
    }
}