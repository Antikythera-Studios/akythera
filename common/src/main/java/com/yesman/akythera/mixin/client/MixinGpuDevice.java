package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.yesman.akythera.mixinaccessor.client.GpuDeviceAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GpuDevice.class)
public abstract class MixinGpuDevice implements GpuDeviceAccessor {
    @Shadow @Final
    private GpuDeviceBackend backend;

    @Override
    public GpuDeviceBackend akythera$backend() {
        return backend;
    }
}
