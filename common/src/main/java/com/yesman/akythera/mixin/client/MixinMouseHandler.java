package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.client.api.camera.CameraComponent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    /// Hands the mouse turn to the local player's [CameraComponent], which turns the camera and decides whether the
    /// player turns along. A player with none keeps its yaw while [LivingEntityPatch#isTurnLocked].
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
        method = "turnPlayer(D)V"
    )
    private void akythera$turnCamera(final LocalPlayer player, final double xo, final double yo) {
        CameraComponent camera = CameraComponent.ofLocalPlayer();

        if (camera != null) {
            if (!camera.turn(xo, yo)) player.turn(xo, yo);
            return;
        }

        boolean turnLocked = AkytheraPatches.getEntityPatch(player) instanceof LivingEntityPatch<?> patch && patch.isTurnLocked();

        player.turn(turnLocked ? 0.0D : xo, yo);
    }
}
