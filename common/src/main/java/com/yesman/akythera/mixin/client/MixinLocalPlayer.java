package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.camera.CameraComponent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {
    /// Turns the player along with its [CameraComponent] right after the movement input is read, so the input of the
    /// tick moves the player in the turned direction.
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER),
        method = "aiStep()V"
    )
    private void akythera$alignWithCamera(final CallbackInfo callbackInfo) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        CameraComponent camera = CameraComponent.holdingView();

        if (camera != null && camera.getPatch().getOriginal() == self) camera.alignAvatar(self);
    }
}
