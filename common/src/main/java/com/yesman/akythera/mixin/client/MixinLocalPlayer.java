package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.cinema.CinemaScenePlayback;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.client.api.camera.CameraComponent;
import com.yesman.akythera.core.cinema.CinemaScene;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
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

    /// Keeps a [CinemaScene.Actor#ignoreBlockCollision] actor inside the blocks the scene places it in.
    @Inject(at = @At(value = "HEAD"), method = "moveTowardsClosestSpace(DD)V", cancellable = true)
    private void akythera$stayInBlocksInCinemaScene(final double x, final double z, final CallbackInfo callbackInfo) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch((LocalPlayer)(Object)this);
        CinemaScenePlayback playback = patch == null ? null : patch.getCinemaScenePlayback();

        if (playback != null && playback.getActor().ignoreBlockCollision()) callbackInfo.cancel();
    }

    /// Lets the sprint key start a sprint in any direction when [EntityMovementComponent#canSprintInAnyDirection].
    ///
    /// Only a held sprint key widens the start. Vanilla's double-tap start also runs through this check, on the
    /// first tick a movement key goes down, so widening it for every key would start a sprint one tick after any
    /// movement key is pressed. Swimming keeps the forward-only start.
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"),
        method = "canStartSprinting()Z"
    )
    private boolean akythera$startSprintInAnyDirection(final ClientInput input) {
        LocalPlayer self = (LocalPlayer)(Object)this;

        if (input.hasForwardImpulse()) return true;

        return input.keyPresses.sprint() && !self.isInWater() && input.getMoveVector().lengthSquared() > 1.0E-10F
            && EntityMovementComponent.canSprintInAnyDirection(self);
    }

    /// Lets a player that [EntityMovementComponent#floatsInWater] sprint, though its eyes above the water read as wading to
    /// [LocalPlayer#isInShallowWater].
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInShallowWater()Z"),
        method = "isSprintingPossible(Z)Z"
    )
    private boolean akythera$sprintWhileFloating(final LocalPlayer self) {
        return self.isInShallowWater() && !EntityMovementComponent.floatsInWater(self);
    }

    /// Lets a player that [EntityMovementComponent#floatsInWater] switch creative flight on with a double jump while it
    /// swims. Flying ends the swim.
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSwimming()Z", ordinal = 0),
        method = "aiStep()V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;jumpTriggerTime:I", opcode = Opcodes.GETFIELD))
    )
    private boolean akythera$toggleFlightWhileFloating(final LocalPlayer self) {
        return self.isSwimming() && !EntityMovementComponent.floatsInWater(self);
    }

    /// Keeps a sprint going in any direction the player moves when [EntityMovementComponent#canSprintInAnyDirection].
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"),
        method = "shouldStopRunSprinting()Z"
    )
    private boolean akythera$keepSprintInAnyDirection(final ClientInput input) {
        LocalPlayer self = (LocalPlayer)(Object)this;

        return input.hasForwardImpulse()
            || input.getMoveVector().lengthSquared() > 1.0E-10F && EntityMovementComponent.canSprintInAnyDirection(self);
    }
}
