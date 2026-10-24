package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.api.animation.notify.impl.ExtendDeathTimeNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class)
public abstract class MixinLivingEntity {
    /// Highest [LivingEntity#deathTime] a dead entity reaches while a rag doll runs: [LivingEntity#tickDeath] adds 1, and
    /// poofs and removes the entity at 20.
    @Unique
    private static final int HELD_DEATH_TIME = 18;

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


    /// Switches off AI, travel and pushing while a rag doll runs.
    @Inject(at = @At(value = "HEAD"), method = "aiStep()V", cancellable = true)
    private void akythera$stopActingInRagdoll(final CallbackInfo callbackInfo) {
        if (akythera$isRagdollRunning()) callbackInfo.cancel();
    }

    /// Takes the entity out of picking (crosshair, melee, projectiles) while a rag doll runs.
    @Inject(at = @At(value = "HEAD"), method = "isPickable()Z", cancellable = true)
    private void akythera$unpickableInRagdoll(final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$isRagdollRunning()) callbackInfo.setReturnValue(false);
    }

    /// Stops other entities from pushing the entity while a rag doll runs.
    @Inject(at = @At(value = "HEAD"), method = "isPushable()Z", cancellable = true)
    private void akythera$unpushableInRagdoll(final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$isRagdollRunning()) callbackInfo.setReturnValue(false);
    }

    /// Keeps a dead entity under the death time vanilla poofs and removes it at while a rag doll runs, or while a
    /// montage holds an [ExtendDeathTimeNotifyState].
    @Inject(at = @At(value = "HEAD"), method = "tickDeath()V")
    private void akythera$holdDeath(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (akythera$isRagdollRunning() || akythera$isDeathTimeExtended()) self.deathTime = Math.min(self.deathTime, HELD_DEATH_TIME);
    }

    /// Ignores every damage but [DamageTypeTags#BYPASSES_INVULNERABILITY] (`/kill`, the void) while a rag doll runs.
    @Inject(at = @At(value = "HEAD"), method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", cancellable = true)
    private void akythera$ignoreDamageInRagdoll(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$isRagdollRunning() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) callbackInfo.setReturnValue(false);
    }

    @Unique
    private boolean akythera$isDeathTimeExtended() {
        ArmatureComponent armatureComponent = ((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch
            ? patch.getArmatureComponent()
            : null;

        if (armatureComponent == null) return false;

        for (AnimationMontagePlayer montagePlayer : armatureComponent.getAnimator().getMontagePlayers()) {
            if (montagePlayer.getActiveNotifyState(ExtendDeathTimeNotifyState.class) != null) return true;
        }

        return false;
    }

    @Unique
    private boolean akythera$isRagdollRunning() {
        ArmatureComponent armatureComponent = ((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch
            ? patch.getArmatureComponent()
            : null;

        return armatureComponent != null && armatureComponent.isRagdollRunning();
    }
}
