package com.yesman.akythera.mixinaccessor.client;

import com.mojang.blaze3d.systems.GpuDeviceBackend;

public interface GpuDeviceAccessor {
    /// The API-specific device `GpuDevice` delegates to. `GpuDevice` itself is backend-neutral and
    /// exposes nothing the compute path needs, which lives below the graphics API.
    GpuDeviceBackend akythera$backend();
}
