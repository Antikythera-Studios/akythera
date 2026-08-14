package com.yesman.akythera.mixinaccessor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.client.input.InputAction;
import com.yesman.akythera.mixin.client.MixinKeyMapping;

public interface KeyMappingAccessor {
    /// Access to mixin field [MixinKeyMapping#akythera$holdTicks].
    int akythera$getHoldTick();

    /// Reverse of [net.minecraft.client.KeyMapping#consumeClick()].
    boolean akythera$consumeRelease();

    /// Accessor to [net.minecraft.client.KeyMapping#key].
    InputConstants.Key akythera$getKey();

    /// Binds [action] to this key's activation slot.
    void akythera$bindAction(InputAction action);

    /// Clears [action]'s slot; returns `true` when no bindings remain on this key.
    boolean akythera$unbindAction(InputAction action);

    /// Runs the per-tick activation state machine; called only for bound key mappings.
    void akythera$dispatchInputActions();
}
