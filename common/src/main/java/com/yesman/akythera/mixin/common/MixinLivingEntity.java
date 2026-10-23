package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class)
public abstract class MixinLivingEntity {
    /// Body yaw a [LockMovementNotifyState.Rotation#LOCKED] window keeps, or NaN outside one.
    @Unique
    private float akythera$lockedYBodyRot = Float.NaN;

    /// Drops the movement input read this tick while a [LockMovementNotifyState] holds, and stops the montages a
    /// [CancelOnMoveNotifyState] holds once there is any.
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=jump"), method = "aiStep()V")
    private void akythera$applyMontageMovementRules(CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof LivingEntityPatch<?> patch)) return;

        ArmatureComponent armatureComponent = patch.getArmatureComponent();

        if (armatureComponent == null) return;

        if (patch.getMovementLock() != null) {
            self.xxa = 0.0F;
            self.zza = 0.0F;
            self.setJumping(false);
            return;
        }

        boolean moving = self instanceof ServerPlayer serverPlayer
            ? serverPlayer.getLastClientMoveIntent().lengthSqr() > 0.0
            : self.xxa != 0.0F || self.zza != 0.0F;

        if (!moving) return;

        Animator animator = armatureComponent.getAnimator();

        for (AnimationMontagePlayer montagePlayer : animator.getMontagePlayers()) {
            if (montagePlayer.getActiveNotifyState(CancelOnMoveNotifyState.class) != null) {
                animator.stopPlayingMontage(montagePlayer.getPlayingMontage().key());
            }
        }
    }

    /// Aims the vanilla body turn at the view yaw while [LivingEntityPatch#faceBodyToView] is sampled, or while the
    /// entity moves when its [EntityMovementComponent] asks for it.
    @ModifyArg(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V"),
        method = "tick()V"
    )
    private float akythera$aimBodyTurn(float yBodyRotTarget) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return yBodyRotTarget;

        if (patch instanceof LivingEntityPatch<?> livingPatch && livingPatch.consumeBodyFacesViewRequest()) return self.getYRot();

        EntityMovementComponent movement = patch.getComponent(EntityMovementComponent.class);

        if (movement == null || !movement.isEnabled() || movement.getBodyRotation() != EntityMovementComponent.BodyRotation.FACE_VIEW_WHILE_MOVING) {
            return yBodyRotTarget;
        }

        double dx = self.getX() - self.xo;
        double dz = self.getZ() - self.zo;

        return dx * dx + dz * dz > 0.0025F ? self.getYRot() : yBodyRotTarget;
    }

    /// Turns the body the way the held [LockMovementNotifyState] asks, over the vanilla head turn.
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V", shift = At.Shift.AFTER),
        method = "tick()V"
    )
    private void akythera$applyRotationLock(CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;
        LockMovementNotifyState lock = ((EntityAccessor)self).akythera$getEntityPatch() instanceof LivingEntityPatch<?> patch
            ? patch.getMovementLock()
            : null;
        LockMovementNotifyState.Rotation rotation = lock == null ? LockMovementNotifyState.Rotation.FREE : lock.rotation();

        if (rotation != LockMovementNotifyState.Rotation.LOCKED) akythera$lockedYBodyRot = Float.NaN;

        switch (rotation) {
            case FACE_VIEW -> self.yBodyRot = self.getYRot();
            case LOCKED -> {
                if (Float.isNaN(akythera$lockedYBodyRot)) akythera$lockedYBodyRot = self.yBodyRot;
                self.yBodyRot = akythera$lockedYBodyRot;
            }
            case FREE -> {
            }
        }
    }
}
