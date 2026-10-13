package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.yesman.akythera.client.core.shader.backend.ComputeBackend;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/// Compute backend over the Vulkan device Minecraft creates when `PreferredGraphicsApi` picks it.
///
/// Compute shaders, storage buffers and the packing built-ins are all core Vulkan 1.0, so there is
/// no capability probe to match OpenGL's — a device that got this far runs the compute path.
public final class VulkanComputeBackend implements ComputeBackend {
    /// GLSL 450 is the level `shaderc` compiles for Vulkan. `AKYTHERA_VULKAN` selects the
    /// push-constant half of a shader's uniform declarations, since Vulkan has no uniform outside a
    /// block.
    private static final String PREAMBLE = """
        #version 450
        #define AKYTHERA_VULKAN
        """;

    private final VulkanDevice device;
    private final int storageBufferOffsetAlignment;
    private final long maxStorageBlockSize;
    private final int maxComputeStorageBlocks;

    private VulkanComputeBackend(
        final VulkanDevice device,
        final int storageBufferOffsetAlignment,
        final long maxStorageBlockSize,
        final int maxComputeStorageBlocks
    ) {
        this.device = device;
        this.storageBufferOffsetAlignment = storageBufferOffsetAlignment;
        this.maxStorageBlockSize = maxStorageBlockSize;
        this.maxComputeStorageBlocks = maxComputeStorageBlocks;
    }

    public static VulkanComputeBackend create() {
        VulkanDevice device = VulkanDevices.current();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceLimits limits = VulkanDevices.limits(device, stack);

            return new VulkanComputeBackend(
                device,
                (int) limits.minStorageBufferOffsetAlignment(),
                Integer.toUnsignedLong(limits.maxStorageBufferRange()),
                limits.maxPerStageDescriptorStorageBuffers()
            );
        }
    }

    @Override
    public ComputeShaderModule compile(final String body) {
        return new VulkanComputeShaderModule(PREAMBLE + body);
    }

    @Override
    public ComputeProgram link(final ComputeShaderModule module, final BarrierFlags... barriers) {
        return new VulkanComputeProgram(device, (VulkanComputeShaderModule) module, barriers);
    }

    @Override
    public GpuBuffer createStorageBuffer(final Supplier<String> label, final ByteBuffer data) {
        // Device-local, filled through the encoder's staging copy, which raises a barrier of its own
        // once the copy is recorded.
        GpuBuffer buffer = VulkanStorageBuffer.create(device, label, data.remaining(), GpuBuffer.USAGE_COPY_DST, 0, false);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);

        return buffer;
    }

    @Override
    public GpuBuffer createVertexStorageBuffer(final Supplier<String> label, final long size) {
        return VulkanStorageBuffer.create(
            device, label, size, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, VK12.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, false
        );
    }

    @Override
    public GpuBuffer createIndexStorageBuffer(final Supplier<String> label, final long size) {
        return VulkanStorageBuffer.create(
            device, label, size, GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, VK12.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, false
        );
    }

    @Override
    public GpuBuffer createMappedStorageBuffer(final Supplier<String> label, final long size) {
        return VulkanStorageBuffer.create(device, label, size, GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, 0, true);
    }

    @Override
    public GpuBuffer createComputeStorageBuffer(final Supplier<String> label, final long size) {
        return VulkanStorageBuffer.create(device, label, size, GpuBuffer.USAGE_COPY_DST, 0, false);
    }

    @Override
    public int storageBufferOffsetAlignment() {
        return storageBufferOffsetAlignment;
    }

    @Override
    public long maxStorageBlockSize() {
        return maxStorageBlockSize;
    }

    @Override
    public int maxComputeStorageBlocks() {
        return maxComputeStorageBlocks;
    }
}
