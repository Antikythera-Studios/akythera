package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeUniform;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

/// A compute pipeline and the descriptor and push constant state one dispatch binds to it.
///
/// The dispatch is recorded into a transient command buffer of its own and handed to
/// `VulkanCommandEncoder#execute`, which closes whatever the encoder was recording and appends ours
/// ahead of everything that follows. That puts the skinning in front of the draw that reads its
/// output within the same submission, which is the ordering the OpenGL path gets from issuing both
/// on one context.
final class VulkanComputeProgram implements ComputeProgram {
    private final VulkanDevice device;
    private final long shaderModule;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private final long pipeline;
    private final VulkanDescriptorSetRing descriptorSets;

    private final IntList storageBindings;
    private final Map<String, VulkanComputeShaderModule.PushConstantRange> pushConstants;
    private final Map<String, ComputeUniform> uniforms = new HashMap<>();

    /// The push constant block, staged host-side and pushed whole at dispatch. Null when the stage
    /// declares none.
    private final ByteBuffer pushConstantBlock;

    private final long barrierStageMask;
    private final long barrierAccessMask;

    /// Built last: its bookkeeping arrays are sized from [#storageBindings], which a field
    /// initializer would run ahead of.
    private final VulkanDispatch dispatch;

    private boolean closed;

    VulkanComputeProgram(final VulkanDevice device, final VulkanComputeShaderModule module, final BarrierFlags... barriers) {
        this.device = device;

        storageBindings = module.storageBindings();
        pushConstants = module.pushConstants();
        pushConstantBlock = module.pushConstantSize() > 0 ? MemoryUtil.memCalloc(module.pushConstantSize()) : null;
        barrierStageMask = toStageMask(barriers);
        barrierAccessMask = toAccessMask(barriers);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handlePointer = stack.callocLong(1);

            VkShaderModuleCreateInfo shaderModuleCreateInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
            shaderModuleCreateInfo.pCode(module.spirv());
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreateShaderModule(device.vkDevice(), shaderModuleCreateInfo, null, handlePointer),
                "Failed to create a compute VkShaderModule"
            );
            shaderModule = handlePointer.get(0);

            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.calloc(storageBindings.size(), stack);

            for (int i = 0; i < storageBindings.size(); i++) {
                layoutBindings.get(i)
                    .binding(storageBindings.getInt(i))
                    .descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK12.VK_SHADER_STAGE_COMPUTE_BIT);
            }

            VkDescriptorSetLayoutCreateInfo setLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default();
            setLayoutCreateInfo.pBindings(layoutBindings);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreateDescriptorSetLayout(device.vkDevice(), setLayoutCreateInfo, null, handlePointer),
                "Failed to create a compute VkDescriptorSetLayout"
            );
            descriptorSetLayout = handlePointer.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();
            pipelineLayoutCreateInfo.pSetLayouts(stack.longs(descriptorSetLayout));

            if (pushConstantBlock != null) {
                VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
                range.get(0).stageFlags(VK12.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushConstantBlock.capacity());
                pipelineLayoutCreateInfo.pPushConstantRanges(range);
            }

            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreatePipelineLayout(device.vkDevice(), pipelineLayoutCreateInfo, null, handlePointer),
                "Failed to create a compute VkPipelineLayout"
            );
            pipelineLayout = handlePointer.get(0);

            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default();
            stage.stage(VK12.VK_SHADER_STAGE_COMPUTE_BIT);
            stage.module(shaderModule);
            stage.pName(stack.UTF8("main"));

            VkComputePipelineCreateInfo.Buffer pipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default();
            pipelineCreateInfo.get(0).stage(stage).layout(pipelineLayout);

            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreateComputePipelines(device.vkDevice(), VK12.VK_NULL_HANDLE, pipelineCreateInfo, null, handlePointer),
                "Failed to create a VkPipeline for the compute stage"
            );
            pipeline = handlePointer.get(0);
        }

        descriptorSets = new VulkanDescriptorSetRing(device, descriptorSetLayout, storageBindings.size());
        dispatch = new VulkanDispatch();
    }

    @Override
    public ComputeProgram.Dispatch begin() {
        return dispatch.open();
    }

    @Override
    public ComputeUniform uniform(final String name) {
        return uniforms.computeIfAbsent(name, key -> {
            VulkanComputeShaderModule.PushConstantRange range = pushConstants.get(key);

            return range == null || pushConstantBlock == null
                ? VulkanComputeUniform.DROPPED
                : new VulkanComputeUniform(pushConstantBlock, range.offset(), range.size());
        });
    }

    /// True once the constructor has built the pipeline. Anything short of that crashes the game
    /// through `VulkanUtils#crashIfFailure`, the way every other Vulkan object creation does.
    @Override
    public boolean isLinked() {
        return !closed;
    }

    @Override
    public String getInfoLog() {
        return "";
    }

    /// A program is closed on resource reload, while the frames that dispatched through it may still
    /// be in flight, so the pipeline and its layouts go through the encoder's destruction queue
    /// rather than straight to `vkDestroy*`. The push constant block is host memory no command
    /// buffer refers to, and goes at once.
    @Override
    public void close() {
        if (closed) return;

        closed = true;
        descriptorSets.close();

        device.createCommandEncoder().queueForDestroy(() -> {
            VK12.vkDestroyPipeline(device.vkDevice(), pipeline, null);
            VK12.vkDestroyPipelineLayout(device.vkDevice(), pipelineLayout, null);
            VK12.vkDestroyDescriptorSetLayout(device.vkDevice(), descriptorSetLayout, null);
            VK12.vkDestroyShaderModule(device.vkDevice(), shaderModule, null);
        });

        if (pushConstantBlock != null) {
            MemoryUtil.memFree(pushConstantBlock);
        }
    }

    /// Which stages the dispatch's writes have to be visible to.
    private static long toStageMask(final BarrierFlags... barriers) {
        long mask = 0L;

        for (BarrierFlags barrier : barriers) {
            mask |= switch (barrier) {
                // OpenGL's shader-storage and atomic-counter barriers both order against any later
                // shader read, and Vulkan draws no distinction between the two kinds of storage.
                case SHADER_STORAGE, ATOMIC_COUNTER -> KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR;
                case VERTEX_ATTRIBUTE, ELEMENT_ARRAY -> KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_INPUT_BIT_KHR;
                case COMMAND -> KHRSynchronization2.VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT_KHR;
            };
        }

        return mask;
    }

    private static long toAccessMask(final BarrierFlags... barriers) {
        long mask = 0L;

        for (BarrierFlags barrier : barriers) {
            mask |= switch (barrier) {
                case SHADER_STORAGE, ATOMIC_COUNTER -> KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR
                    | KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
                case VERTEX_ATTRIBUTE -> KHRSynchronization2.VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT_KHR;
                case ELEMENT_ARRAY -> KHRSynchronization2.VK_ACCESS_2_INDEX_READ_BIT_KHR;
                case COMMAND -> KHRSynchronization2.VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT_KHR;
            };
        }

        return mask;
    }

    /// Reused across dispatches: only one dispatch is ever open, on the render thread.
    private final class VulkanDispatch implements ComputeProgram.Dispatch {
        private final long[] boundBuffers = new long[storageBindings.size()];
        private final long[] boundOffsets = new long[storageBindings.size()];
        private final long[] boundRanges = new long[storageBindings.size()];

        /// Every descriptor set this scope wrote, released together on [#close].
        private final LongList takenSets = new LongArrayList();

        private VkCommandBuffer commandBuffer;

        /// Whether a binding moved since the last set was written and bound.
        private boolean bindingsChanged;

        VulkanDispatch open() {
            commandBuffer = device.createCommandEncoder().allocateAndBeginTransientCommandBuffer();
            bindingsChanged = true;

            VK12.vkCmdBindPipeline(commandBuffer, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);

            return this;
        }

        @Override
        public ComputeProgram.Dispatch bindStorageBuffer(final int binding, final GpuBuffer buffer) {
            return bind(binding, ((VulkanGpuBuffer) buffer).vkBuffer(), 0L, buffer.size());
        }

        @Override
        public ComputeProgram.Dispatch bindStorageBuffer(final int binding, final GpuBufferSlice slice) {
            return bind(binding, ((VulkanGpuBuffer) slice.buffer()).vkBuffer(), slice.offset(), slice.length());
        }

        @Override
        public void dispatch(final int countX, final int countY, final int countZ) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (bindingsChanged) {
                    long descriptorSet = descriptorSets.acquire();
                    takenSets.add(descriptorSet);

                    VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(boundBuffers.length, stack);
                    VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(boundBuffers.length, stack);

                    for (int i = 0; i < boundBuffers.length; i++) {
                        if (boundBuffers[i] == VK12.VK_NULL_HANDLE) {
                            throw new IllegalStateException("Storage buffer binding " + storageBindings.getInt(i) + " was left unbound");
                        }

                        bufferInfos.get(i).buffer(boundBuffers[i]).offset(boundOffsets[i]).range(boundRanges[i]);
                        writes.get(i)
                            .sType$Default()
                            .dstSet(descriptorSet)
                            .dstBinding(storageBindings.getInt(i))
                            .descriptorCount(1)
                            .descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .pBufferInfo(VkDescriptorBufferInfo.create(bufferInfos.address(i), 1));
                    }

                    VK12.vkUpdateDescriptorSets(device.vkDevice(), writes, null);
                    VK12.vkCmdBindDescriptorSets(
                        commandBuffer, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, stack.longs(descriptorSet), null
                    );

                    bindingsChanged = false;
                }

                if (pushConstantBlock != null) {
                    VK12.vkCmdPushConstants(commandBuffer, pipelineLayout, VK12.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstantBlock);
                }

                VK12.vkCmdDispatch(commandBuffer, countX, countY, countZ);

                VkMemoryBarrier2.Buffer memoryBarrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
                memoryBarrier.get(0)
                    .srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .srcAccessMask(KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)
                    .dstStageMask(barrierStageMask)
                    .dstAccessMask(barrierAccessMask);

                VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack).sType$Default();
                dependencyInfo.pMemoryBarriers(memoryBarrier);

                KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
            }
        }

        @Override
        public void close() {
            VulkanUtils.crashIfFailure(device, VK12.vkEndCommandBuffer(commandBuffer), "Failed to end the compute VkCommandBuffer");
            device.createCommandEncoder().execute(commandBuffer);

            for (int i = 0; i < takenSets.size(); i++) {
                descriptorSets.release(takenSets.getLong(i));
            }

            takenSets.clear();

            java.util.Arrays.fill(boundBuffers, VK12.VK_NULL_HANDLE);
            commandBuffer = null;
        }

        private ComputeProgram.Dispatch bind(final int binding, final long vkBuffer, final long offset, final long range) {
            int index = storageBindings.indexOf(binding);

            if (index < 0) {
                throw new IllegalArgumentException("The compute stage declares no storage buffer at binding " + binding);
            }

            boundBuffers[index] = vkBuffer;
            boundOffsets[index] = offset;
            boundRanges[index] = range;
            bindingsChanged = true;

            return this;
        }
    }
}
