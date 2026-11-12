package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.camera.CameraComponent;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.client.input.InputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Drives per-tick input activation dispatch, the frame boundary the skinning ring rotates on, and the crosshair of a
/// [CameraComponent] holding the view.
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    public @Nullable HitResult hitResult;

    @Shadow
    public @Nullable Entity crosshairPickEntity;

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

    /// Picks the crosshair target from the camera instead of from the player's eyes.
    @Inject(at = @At("TAIL"), method = "pick(F)V")
    private void akythera$pickFromCamera(final float partialTicks, final CallbackInfo callbackInfo) {
        CameraComponent camera = CameraComponent.holdingView();

        if (camera == null || hitResult == null) return;

        hitResult = camera.pickCrosshair(partialTicks);
        crosshairPickEntity = hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
    }

    @Inject(at = @At("HEAD"), method = "startAttack()Z")
    private void akythera$aimAttack(final CallbackInfoReturnable<Boolean> callbackInfo) {
        CameraComponent camera = CameraComponent.holdingView();

        if (camera != null) camera.correctAim();
    }

    @Inject(at = @At("HEAD"), method = "continueAttack(Z)V")
    private void akythera$aimDigging(final boolean down, final CallbackInfo callbackInfo) {
        CameraComponent camera = down ? CameraComponent.holdingView() : null;

        if (camera != null) camera.correctAim();
    }

    @Inject(at = @At("HEAD"), method = "startUseItem()V")
    private void akythera$aimItemUse(final CallbackInfo callbackInfo) {
        CameraComponent camera = CameraComponent.holdingView();

        if (camera != null) camera.correctAim();
    }
}
