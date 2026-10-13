package com.yesman.akythera.client.core.shader.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/// The graphics API the compute path runs on.
///
/// Also the only place storage buffers are allocated. `GpuDevice#createBuffer` takes the usage bits
/// [GpuBuffer] declares, none of which mean *storage buffer*, and the Vulkan backend's
/// `VulkanConst#bufferUsageToVk` maps exactly those bits — so a buffer it hands out can never be
/// bound as one.
public interface ComputeBackend {
    /// Compiles one compute stage. `body` carries no `#version` line: the preamble is a property of
    /// the API, so each backend prepends its own along with whatever the language level has to be
    /// switched on by name.
    ComputeShaderModule compile(String body);

    ComputeProgram link(ComputeShaderModule module, BarrierFlags... barriers);

    /// A storage buffer holding `data`, never written again.
    GpuBuffer createStorageBuffer(Supplier<String> label, ByteBuffer data);

    /// A storage buffer the graphics pipeline also reads as vertex attributes.
    GpuBuffer createVertexStorageBuffer(Supplier<String> label, long size);

    /// A storage buffer the graphics pipeline also reads as indices.
    GpuBuffer createIndexStorageBuffer(Supplier<String> label, long size);

    /// A storage buffer whose contents the host rewrites through `GpuBuffer#map`.
    GpuBuffer createMappedStorageBuffer(Supplier<String> label, long size);

    /// A device-local storage buffer that compute dispatches read and write across frames. Its
    /// contents start undefined.
    GpuBuffer createComputeStorageBuffer(Supplier<String> label, long size);

    /// Byte alignment a storage buffer binding offset must satisfy.
    int storageBufferOffsetAlignment();

    /// Largest `layout(std430) buffer` block the device accepts, in bytes.
    long maxStorageBlockSize();

    /// Most storage buffers one compute stage may bind.
    int maxComputeStorageBlocks();
}
