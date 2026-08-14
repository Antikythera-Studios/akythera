package com.yesman.akythera.client.core.shader.backend.impl.vulkan;

import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.yesman.akythera.mixinaccessor.client.GpuDeviceAccessor;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VK12;

public final class VulkanDevices {
    private VulkanDevices() {
    }

    /// The Vulkan device behind [RenderSystem#getDevice].
    public static VulkanDevice current() {
        GpuDeviceBackend backend = ((GpuDeviceAccessor) RenderSystem.getDevice()).akythera$backend();

        if (backend instanceof VulkanDevice vulkanDevice) {
            return vulkanDevice;
        }

        throw new IllegalStateException("The graphics backend is not Vulkan but " + backend.getClass().getName());
    }

    /// `VulkanDevice` closes its `VulkanPhysicalDevice` at the end of its constructor and publishes
    /// only the handful of limits [com.mojang.blaze3d.systems.DeviceLimits] carries, none of which
    /// describe storage buffers. The physical device the logical one was created from answers for
    /// itself.
    public static VkPhysicalDeviceLimits limits(final VulkanDevice device, final org.lwjgl.system.MemoryStack stack) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        VK12.vkGetPhysicalDeviceProperties(device.vkDevice().getPhysicalDevice(), properties);

        return properties.limits();
    }
}
