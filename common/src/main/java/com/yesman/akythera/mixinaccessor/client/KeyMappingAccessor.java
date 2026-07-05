package com.yesman.akythera.mixinaccessor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.mixin.client.MixinKeyMapping;

public interface KeyMappingAccessor {
    /// Access to mixin field [MixinKeyMapping#epicfight$holdTicks]
    int akythera$getHoldTick();

    /// Reverse operation of [net.minecraft.client.KeyMapping#consumeClick()]
    boolean akythera$consumeRelease();

    /// Accessor to [net.minecraft.client.KeyMapping#key]
    InputConstants.Key akythera$getKey();
}
