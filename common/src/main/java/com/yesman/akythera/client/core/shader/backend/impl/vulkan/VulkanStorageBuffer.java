package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.function.Supplier;

/// A [VulkanGpuBuffer] whose `VkBuffer` carries `VK_BUFFER_USAGE_STORAGE_BUFFER_BIT`.
///
/// `VulkanConst#bufferUsageToVk` maps only the usage bits [GpuBuffer] declares, and none of them
/// mean *storage buffer*, so every buffer the device hands out is unbindable as one. This allocates
/// through the same VMA allocator with that bit added, and reports the [GpuBuffer] usage bits that
/// let the rest of Blaze3D keep working with it — `USAGE_COPY_DST` so `CommandEncoder#writeToBuffer`
/// accepts it, `USAGE_VERTEX` so `RenderPass#setVertexBuffer` does.
final class VulkanStorageBuffer extends VulkanGpuBuffer implements Destroyable {
    private final VulkanDevice device;
    private final long allocation;

    private boolean closed;
    private int mappingRefCount;

    private VulkanStorageBuffer(
        final VulkanDevice device,
        final long vkBuffer,
        final long allocation,
        final @GpuBuffer.Usage int usage,
        final long size
    ) {
        super(vkBuffer, usage, size);

        this.device = device;
        this.allocation = allocation;
    }

    static VulkanStorageBuffer create(
        final VulkanDevice device,
        final Supplier<String> label,
        final long size,
        final @GpuBuffer.Usage int usage,
        final int extraVkUsage,
        final boolean hostVisible
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
            bufferCreateInfo.size(size);
            bufferCreateInfo.usage(
                VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK12.VK_BUFFER_USAGE_TRANSFER_DST_BIT | extraVkUsage
            );
            bufferCreateInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
            bufferCreateInfo.pQueueFamilyIndices(null);

            VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.calloc(stack);
            allocationCreateInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO);

            if (hostVisible) {
                allocationCreateInfo.requiredFlags(
                    VK12.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK12.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
                allocationCreateInfo.flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            }

            LongBuffer bufferPointer = stack.callocLong(1);
            PointerBuffer allocationPointer = stack.callocPointer(1);

            VulkanUtils.crashIfFailure(
                device,
                Vma.vmaCreateBuffer(device.vma(), bufferCreateInfo, allocationCreateInfo, bufferPointer, allocationPointer, null),
                "Failed to allocate a storage VkBuffer"
            );

            long vkBuffer = bufferPointer.get(0);
            device.instance().debug().setObjectName(device.vkDevice(), VK12.VK_OBJECT_TYPE_BUFFER, vkBuffer, label);

            return new VulkanStorageBuffer(device, vkBuffer, allocationPointer.get(0), usage, size);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;

        if (mappingRefCount != 0) {
            throw new IllegalStateException("Attempt to close a mapped buffer");
        }

        device.createCommandEncoder().queueForDestroy(this);
    }

    @Override
    public void destroy() {
        Vma.vmaDestroyBuffer(device.vma(), vkBuffer(), allocation);
    }

    @Override
    public GpuBufferSlice.MappedView map(final long offset, final long length, final boolean read, final boolean write) {
        if (closed) {
            throw new IllegalStateException("Buffer already closed");
        }

        if (offset < 0L || length < 0L || offset + length > size()) {
            throw new IllegalArgumentException(
                "Cannot map " + length + " bytes at offset " + offset + " from a " + size() + " size buffer"
            );
        }

        mappingRefCount++;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            VulkanUtils.crashIfFailure(device, Vma.vmaMapMemory(device.vma(), allocation, pointer), "Failed to map buffer");

            ByteBuffer mapped = MemoryUtil.memByteBuffer(pointer.get(0) + offset, (int) length);

            return new GpuBufferSlice.MappedView(slice(offset, length), mapped, new Unmap());
        }
    }

    /// Idempotent: [GpuBufferSlice.MappedView] is an `AutoCloseable` a caller may close twice, and a
    /// second `vmaUnmapMemory` would unbalance the allocation's map count.
    private final class Unmap implements Runnable {
        private boolean unmapped;

        @Override
        public void run() {
            if (unmapped) return;

            unmapped = true;
            mappingRefCount--;
            Vma.vmaUnmapMemory(device.vma(), allocation);
        }
    }
}
