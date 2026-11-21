package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.api.animation.notify.impl.ExtendDeathTimeNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.LivingBodyTurnEvent;
import com.yesman.akythera.api.event.entity.LivingDeathEvent;
import com.yesman.akythera.api.event.entity.LivingHurtEvent;
import com.yesman.akythera.api.event.entity.LivingJumpEvent;
import com.yesman.akythera.api.event.entity.LivingTravelEvent;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akythera.mixinaccessor.common.LivingEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class)
public abstract class MixinLivingEntity implements LivingEntityAccessor {
    /// Highest [LivingEntity#deathTime] a dead entity reaches while a rag doll runs: [LivingEntity#tickDeath] adds 1, and
    /// poofs and removes the entity at 20.
    @Unique
    private static final int HELD_DEATH_TIME = 18;

    /// Body yaw a [LockMovementNotifyState.Rotation#LOCKED] window keeps, or NaN outside one.
    @Unique
    private float akythera$lockedYBodyRot = Float.NaN;

    /// Runs the [BrainComponent] of an entity that is no [Mob], following the full tick split of
    /// [Mob#serverAiStep].
    @Inject(at = @At("HEAD"), method = "serverAiStep()V")
    private void akythera$tickBrain(CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        BrainComponent brain = patch.getComponent(BrainComponent.class);

        if (brain != null) brain.tick((self.tickCount + self.getId()) % 2 == 0 || self.tickCount <= 1);
    }

    @Shadow
    private int noJumpDelay;

    /// The sprint boost the running [LivingEntity#jumpFromGround] added, zero outside one or without sprint.
    @Unique
    private Vec3 akythera$sprintBoost = Vec3.ZERO;

    /// Posts [BuiltInEventHooks.Entity#JUMP_INPUT] and takes the jump input its listeners leave.
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=jump"), method = "aiStep()V")
    @SuppressWarnings("unchecked")
    private void akythera$postJumpInput(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        LivingJumpEvent.Input event = BuiltInEventHooks.Entity.JUMP_INPUT.postWithListener(
            new LivingJumpEvent.Input((EntityPatch<LivingEntity>)patch, self.isJumping()), patch.getEntityEventListener()
        );

        self.setJumping(event.isJumping());
    }

    /// Posts [BuiltInEventHooks.Entity#TRAVEL_PRE].
    ///
    /// Posted after the jump block because NeoForge's `aiStep` calls `jumpInFluid` where vanilla calls `jumpInLiquid`.
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=travel"), method = "aiStep()V")
    @SuppressWarnings("unchecked")
    private void akythera$postTravelPre(final CallbackInfo callbackInfo) {
        if (!(((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        BuiltInEventHooks.Entity.TRAVEL_PRE.postWithListener(
            new LivingTravelEvent.Pre((EntityPatch<LivingEntity>)patch), patch.getEntityEventListener()
        );
    }

    /// Posts [BuiltInEventHooks.Entity#TRAVEL_POST].
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER),
        method = "aiStep()V"
    )
    @SuppressWarnings("unchecked")
    private void akythera$postTravelPost(final CallbackInfo callbackInfo) {
        if (!(((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        BuiltInEventHooks.Entity.TRAVEL_POST.postWithListener(
            new LivingTravelEvent.Post((EntityPatch<LivingEntity>)patch), patch.getEntityEventListener()
        );
    }

    /// Posts [BuiltInEventHooks.Entity#BODY_TURN].
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V", shift = At.Shift.AFTER),
        method = "tick()V"
    )
    @SuppressWarnings("unchecked")
    private void akythera$postBodyTurn(final CallbackInfo callbackInfo) {
        if (!(((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        BuiltInEventHooks.Entity.BODY_TURN.postWithListener(
            new LivingBodyTurnEvent((EntityPatch<LivingEntity>)patch), patch.getEntityEventListener()
        );
    }

    /// Stores the sprint boost for [#akythera$postJumped].
    @ModifyArg(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
        method = "jumpFromGround()V"
    )
    private Vec3 akythera$storeSprintBoost(final Vec3 boost) {
        akythera$sprintBoost = boost;

        return boost;
    }

    /// Posts [BuiltInEventHooks.Entity#JUMPED], and swaps the sprint boost for the one its listeners leave.
    @Inject(at = @At(value = "RETURN"), method = "jumpFromGround()V")
    @SuppressWarnings("unchecked")
    private void akythera$postJumped(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 vanillaBoost = akythera$sprintBoost;

        akythera$sprintBoost = Vec3.ZERO;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        LivingJumpEvent.Jumped event = BuiltInEventHooks.Entity.JUMPED.postWithListener(
            new LivingJumpEvent.Jumped((EntityPatch<LivingEntity>)patch, vanillaBoost), patch.getEntityEventListener()
        );

        self.addDeltaMovement(event.getSprintBoost().subtract(vanillaBoost));
    }

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

    /// Posts [BuiltInEventHooks.Entity#DEATH] on the first death tick.
    ///
    /// [LivingEntity#die] can't post it: [ServerPlayer#die] overrides it without calling the super method. Every
    /// living entity, players included, runs [LivingEntity#tickDeath] from the tick it is dead on.
    @Inject(at = @At(value = "HEAD"), method = "tickDeath()V")
    @SuppressWarnings("unchecked")
    private void akythera$postDeath(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self.deathTime != 0 || self.level().isClientSide()) return;
        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        BuiltInEventHooks.Entity.DEATH.postWithListener(
            new LivingDeathEvent((EntityPatch<LivingEntity>)patch, self.getLastDamageSource()), patch.getEntityEventListener()
        );
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

    /// Posts [BuiltInEventHooks.Entity#HURT] for a hit that can land, and takes the damage its listeners leave.
    @ModifyVariable(
        at = @At(value = "HEAD"),
        method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        argsOnly = true
    )
    @SuppressWarnings("unchecked")
    private float akythera$postHurt(final float damage, final ServerLevel level, final DamageSource source) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self.isInvulnerableTo(level, source) || self.isDeadOrDying()) return damage;
        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return damage;

        return BuiltInEventHooks.Entity.HURT.postWithListener(
            new LivingHurtEvent((EntityPatch<LivingEntity>)patch, source, damage), patch.getEntityEventListener()
        ).getAmount();
    }

    /// Lets the brain of this entity feel damage it took.
    @Inject(at = @At(value = "RETURN"), method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private void akythera$feelDamage(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfoReturnable<Boolean> callbackInfo) {
        BehaviorTreeInstance tree = callbackInfo.getReturnValueZ() ? BrainComponent.treeOf((LivingEntity)(Object)this) : null;

        if (tree != null) tree.feelDamage(source);
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

    @Override
    public int akythera$getNoJumpDelay() {
        return noJumpDelay;
    }

    @Override
    public void akythera$setNoJumpDelay(final int noJumpDelay) {
        this.noJumpDelay = noJumpDelay;
    }
}
