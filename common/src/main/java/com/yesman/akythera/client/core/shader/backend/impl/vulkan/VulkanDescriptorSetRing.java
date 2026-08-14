package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

/// Hands out one descriptor set per dispatch and takes it back once the submission that used it has
/// completed.
///
/// A set cannot be rewritten while a command buffer still pending on the queue reads it, so a
/// released set waits behind a [GpuFence] before returning to the free list. Sets are never freed
/// individually — an exhausted ring adds another pool instead, and the whole lot goes at [#close].
final class VulkanDescriptorSetRing implements AutoCloseable {
    private static final int SETS_PER_POOL = 64;

    private final VulkanDevice device;
    private final long setLayout;
    private final int bindingCount;

    private final LongList pools = new LongArrayList();
    private final LongList available = new LongArrayList();
    private final LongList pendingRelease = new LongArrayList();
    private final List<InFlight> inFlight = new ArrayList<>();

    VulkanDescriptorSetRing(final VulkanDevice device, final long setLayout, final int bindingCount) {
        this.device = device;
        this.setLayout = setLayout;
        this.bindingCount = bindingCount;
    }

    long acquire() {
        reclaimCompleted();

        if (available.isEmpty()) {
            sealPending();
            reclaimCompleted();
        }

        if (available.isEmpty()) {
            addPool();
        }

        return available.removeLong(available.size() - 1);
    }

    void release(final long descriptorSet) {
        pendingRelease.add(descriptorSet);
    }

    @Override
    public void close() {
        for (int i = 0; i < inFlight.size(); i++) {
            inFlight.get(i).fence.close();
        }

        inFlight.clear();
        pendingRelease.clear();
        available.clear();

        // Sets handed out by these pools may still be read by a command buffer on the queue, so the
        // pools go through the encoder's destruction queue rather than straight to vkDestroy.
        LongList doomed = new LongArrayList(pools);
        device.createCommandEncoder().queueForDestroy(() -> {
            for (int i = 0; i < doomed.size(); i++) {
                VK12.vkDestroyDescriptorPool(device.vkDevice(), doomed.getLong(i), null);
            }
        });

        pools.clear();
    }

    /// Closes the batch of sets released so far behind one fence. They were all used no later than
    /// the submission in flight now, so one fence covers the batch.
    private void sealPending() {
        if (pendingRelease.isEmpty()) return;

        inFlight.add(new InFlight(new LongArrayList(pendingRelease), RenderSystem.getDevice().createCommandEncoder().createFence()));
        pendingRelease.clear();
    }

    private void reclaimCompleted() {
        inFlight.removeIf(batch -> {
            if (!batch.fence.awaitCompletion(0L)) {
                return false;
            }

            batch.fence.close();
            available.addAll(batch.sets);

            return true;
        });
    }

    private void addPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0).type(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(SETS_PER_POOL * bindingCount);

            VkDescriptorPoolCreateInfo poolCreateInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default();
            poolCreateInfo.maxSets(SETS_PER_POOL);
            poolCreateInfo.pPoolSizes(poolSizes);

            LongBuffer poolPointer = stack.callocLong(1);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreateDescriptorPool(device.vkDevice(), poolCreateInfo, null, poolPointer),
                "Failed to create a compute VkDescriptorPool"
            );

            long pool = poolPointer.get(0);
            pools.add(pool);

            LongBuffer layouts = stack.callocLong(SETS_PER_POOL);

            for (int i = 0; i < SETS_PER_POOL; i++) {
                layouts.put(i, setLayout);
            }

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default();
            allocateInfo.descriptorPool(pool);
            allocateInfo.pSetLayouts(layouts);

            LongBuffer sets = stack.callocLong(SETS_PER_POOL);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkAllocateDescriptorSets(device.vkDevice(), allocateInfo, sets),
                "Failed to allocate compute VkDescriptorSets"
            );

            for (int i = 0; i < SETS_PER_POOL; i++) {
                available.add(sets.get(i));
            }
        }
    }

    private record InFlight(LongList sets, GpuFence fence) {
    }
}
