package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.client.input.InputManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Drives per-tick input activation dispatch, and the frame boundary the skinning ring rotates on.
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    /// Dispatches input actions before vanilla reads the keys. Skipped with vanilla's while a screen or an overlay is open.
    @Inject(at = @At("HEAD"), method = "handleKeybinds()V")
    private void akythera$dispatchInput(CallbackInfo callbackInfo) {
        InputManager.getInstance().dispatchInputActions();
    }

    /// Rotates the skinning ring once a frame, which is the latency its three slots are sized for.
    /// TAIL is past this frame's `CommandEncoder#submit`, so every slice handed out during the frame
    /// stays valid for the whole of it and the fence lands on a submit that is already recorded.
    @Inject(at = @At("TAIL"), method = "renderFrame(Z)V")
    private void akythera$endSkinnedMeshFrame(boolean advanceGameTime, CallbackInfo callbackInfo) {
        PatchModelRenderer.getInstance().endFrame();
        TrailRenderer.getInstance().endFrame();
    }
}
