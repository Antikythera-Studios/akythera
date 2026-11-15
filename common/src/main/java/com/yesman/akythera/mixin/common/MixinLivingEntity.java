package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.impl.BlockJumpNotifyState;
import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.api.animation.notify.impl.ExtendDeathTimeNotifyState;
import com.yesman.akythera.api.animation.notify.impl.KeepMomentumNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.LivingDeathEvent;
import com.yesman.akythera.api.event.entity.LivingHurtEvent;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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
public abstract class MixinLivingEntity {
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

    /// Ticks a press that couldn't jump yet stays wanted once the key is let go.
    @Unique
    private static final int JUMP_BUFFER_TICKS = 5;

    /// Whether the jump key was let go since the entity last jumped from the ground.
    @Unique
    private boolean akythera$jumpReleased = true;

    /// Ticks the last press still asks for a jump, see [#JUMP_BUFFER_TICKS].
    @Unique
    private int akythera$bufferedJumpTicks;

    @Shadow
    protected boolean jumping;

    /// Holds a jump from the ground back while a [BlockJumpNotifyState] plays. When the [EntityMovementComponent] asks for
    /// a new press per jump, a press jumps once, as soon as the ground and the notify state allow it within
    /// [#JUMP_BUFFER_TICKS] after the key is let go (or any time while it stays held).
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=jump"), method = "aiStep()V")
    private void akythera$applyJumpRules(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof LivingEntityPatch<?> patch)) return;

        EntityMovementComponent movement = patch.getComponent(EntityMovementComponent.class);
        boolean pressPerJump = movement != null && movement.isEnabled() && movement.jumpRequiresPress();

        if (pressPerJump) {
            if (!jumping) akythera$jumpReleased = true;

            if (jumping && akythera$jumpReleased) {
                akythera$bufferedJumpTicks = JUMP_BUFFER_TICKS;
            } else if (akythera$bufferedJumpTicks > 0) {
                akythera$bufferedJumpTicks--;
            }
        }

        if (!self.onGround() || self.isInLiquid() || self.onClimbable()) return;

        ArmatureComponent armatureComponent = patch.getArmatureComponent();
        BlockJumpNotifyState block = armatureComponent == null ? null : armatureComponent.getAnimator().getActiveNotifyState(BlockJumpNotifyState.class);
        boolean blocked = block != null && patch.getFallHeight() >= block.minFallHeight();

        self.setJumping((pressPerJump ? akythera$bufferedJumpTicks > 0 : jumping) && !blocked);
    }

    /// Lowest the look may point, as the up part of the look vector, for a swimming entity to stay on the surface.
    @Unique
    private static final double SURFACE_SWIM_MIN_LOOK = -0.35;

    /// Fastest vertical speed, in blocks per tick, the float takes over from. Jumps, falls into the water and the climb
    /// out at a shore keep their momentum.
    @Unique
    private static final double FLOAT_TAKEOVER_SPEED = 0.25;

    /// Ticks between two jumps from the ground, as vanilla waits.
    @Unique
    private static final int JUMP_DELAY_TICKS = 10;

    @Shadow
    private int noJumpDelay;

    /// Keeps an entity at its [EntityMovementComponent#surfaceFloat] height near the surface, until it sneaks or looks
    /// down while swimming to dive. Jumping against a shore rises as in vanilla to climb out, and a jump while standing
    /// in water too shallow to float in is a jump from the ground.
    ///
    /// Overrides the speed after the jump block because NeoForge's `aiStep` calls `jumpInFluid` where vanilla calls
    /// `jumpInLiquid`.
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=travel"), method = "aiStep()V")
    private void akythera$floatAtSurface(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!self.isInWater() || !self.isAffectedByFluids() || self.isPassenger()) return;
        if (self.isShiftKeyDown() || self.isSwimming() && self.getLookAngle().y < SURFACE_SWIM_MIN_LOOK) return;
        if (jumping && self.horizontalCollision) return;
        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof EntityPatch<?> patch)) return;

        EntityMovementComponent movement = patch.getComponent(EntityMovementComponent.class);
        double speed = movement == null || !movement.isEnabled() ? Double.NaN : movement.surfaceFloatSpeed();

        if (Double.isNaN(speed)) return;

        if (jumping && self.onGround() && speed <= 0.0D) {
            if (noJumpDelay == 0) {
                self.jumpFromGround();
                noJumpDelay = JUMP_DELAY_TICKS;
            }

            return;
        }

        Vec3 motion = self.getDeltaMovement();

        if (Math.abs(motion.y) <= FLOAT_TAKEOVER_SPEED) self.setDeltaMovement(motion.x, speed, motion.z);
    }

    /// Speed a [KeepMomentumNotifyState] holds through this tick's [LivingEntity#travel], or null outside one.
    @Unique
    private @Nullable Vec3 akythera$keptMomentum;

    /// Drops the movement input and remembers the speed while a [KeepMomentumNotifyState] holds an airborne owner.
    @Inject(at = @At(value = "CONSTANT", args = "stringValue=travel"), method = "aiStep()V")
    private void akythera$holdMomentum(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        akythera$keptMomentum = null;

        if (self.onGround() || self.isInLiquid() || self.isPassenger() || self.isFallFlying()) return;
        if (self instanceof Player player && player.getAbilities().flying) return;
        if (!(((EntityAccessor)self).akythera$getEntityPatch() instanceof LivingEntityPatch<?> patch)) return;

        ArmatureComponent armatureComponent = patch.getArmatureComponent();

        if (armatureComponent == null || armatureComponent.getAnimator().getActiveNotifyState(KeepMomentumNotifyState.class) == null) return;

        self.xxa = 0.0F;
        self.zza = 0.0F;
        akythera$keptMomentum = self.getDeltaMovement();
    }

    /// Gives back the horizontal speed the air drag took in [LivingEntity#travel], unless the owner ran into a wall.
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER),
        method = "aiStep()V"
    )
    private void akythera$keepMomentum(final CallbackInfo callbackInfo) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (akythera$keptMomentum == null || self.horizontalCollision) return;

        self.setDeltaMovement(akythera$keptMomentum.x, self.getDeltaMovement().y, akythera$keptMomentum.z);
    }

    /// Turns the body to face the way it flies while a [KeepMomentumNotifyState] holds it in the air.
    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V", shift = At.Shift.AFTER),
        method = "tick()V"
    )
    private void akythera$faceMomentum(final CallbackInfo callbackInfo) {
        if (akythera$keptMomentum == null || akythera$keptMomentum.horizontalDistanceSqr() < 1.0E-4D) return;

        ((LivingEntity)(Object)this).yBodyRot = (float)(Mth.atan2(akythera$keptMomentum.z, akythera$keptMomentum.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    @Inject(at = @At(value = "HEAD"), method = "jumpFromGround()V")
    private void akythera$markJumped(final CallbackInfo callbackInfo) {
        akythera$jumpReleased = false;
        akythera$bufferedJumpTicks = 0;
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

    /// Aims the sprint jump boost along the movement input, not the view yaw, when
    /// [EntityMovementComponent#canSprintInAnyDirection].
    @ModifyArg(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
        method = "jumpFromGround()V"
    )
    private Vec3 akythera$aimSprintJumpBoost(final Vec3 boost) {
        LivingEntity self = (LivingEntity)(Object)this;
        float inputLength = Mth.sqrt(self.xxa * self.xxa + self.zza * self.zza);

        if (inputLength < 1.0E-5F || !EntityMovementComponent.canSprintInAnyDirection(self)) return boost;

        float sin = Mth.sin(self.getYRot() * Mth.DEG_TO_RAD);
        float cos = Mth.cos(self.getYRot() * Mth.DEG_TO_RAD);
        double length = boost.horizontalDistance();
        float strafe = self.xxa / inputLength;
        float forward = self.zza / inputLength;

        return new Vec3((strafe * cos - forward * sin) * length, boost.y, (forward * cos + strafe * sin) * length);
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
}
