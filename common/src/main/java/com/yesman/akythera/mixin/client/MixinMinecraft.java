package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.input.InputManager;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Drives per-tick input activation dispatch.
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(at = @At("TAIL"), method = "tick()V")
    private void akythera$dispatchInput(CallbackInfo callbackInfo) {
        for (final KeyMapping keyMapping : InputManager.getInstance().boundKeyMappings()) {
            ((KeyMappingAccessor) keyMapping).akythera$dispatchInputActions();
        }
    }
}
