package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.animation.notify.impl.NoGravityNotifyState;
import com.yesman.akythera.api.cinema.CinemaScenePlayback;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityRemoveEvent;
import com.yesman.akythera.api.event.entity.TickEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class)
public abstract class MixinEntity implements EntityAccessor {
    /// A module that subscribes events triggered from original entity,
    @Unique @Nullable
    private EntityPatch<?> akythera$entityPatch;

    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V"
    )
    private void init(EntityType<?> type, Level level, CallbackInfo callbackInfo) {
        try {
            akythera$entityPatch = ComponentsFactory.getImpl().getEntityPatch((Entity)(Object) this);
        } catch (Exception e) {
            Akythera.LOGGER.error("Failed at generating entity patch of entity type {}.", getClass().getSimpleName(), e);
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "tick()V")
    public void preTick(CallbackInfo callbackInfo) {
        if (akythera$entityPatch != null) {
            BuiltInEventHooks.Entity.TICK_PRE.postWithListener(
                new TickEvent.CommonPre(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
            );

            if (akythera$entityPatch.isLogicalClient()) {
                BuiltInEventHooks.Entity.CLIENT_TICK_PRE.postWithListener(
                    new TickEvent.ClientPre(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
                );
            } else {
                BuiltInEventHooks.Entity.SERVER_TICK_PRE.postWithListener(
                    new TickEvent.ServerPre(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
                );
            }
        }
    }

    @Inject(at = @At(value = "RETURN"), method = "tick()V")
    public void postTick(CallbackInfo callbackInfo) {
        if (akythera$entityPatch != null) {
            BuiltInEventHooks.Entity.TICK_POST.postWithListener(
                new TickEvent.CommonPost(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
            );

            if (akythera$entityPatch.isLogicalClient()) {
                BuiltInEventHooks.Entity.CLIENT_TICK_POST.postWithListener(
                    new TickEvent.ClientPost(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
                );
            } else {
                BuiltInEventHooks.Entity.SERVER_TICK_POST.postWithListener(
                    new TickEvent.ServerPost(akythera$entityPatch), akythera$entityPatch.getEntityEventListener()
                );
            }
        }
    }

    /// Posts [BuiltInEventHooks.Entity#REMOVED] the first time the entity is marked removed.
    @Inject(at = @At(value = "HEAD"), method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")
    private void akythera$postRemoved(final Entity.RemovalReason reason, final CallbackInfo callbackInfo) {
        if (akythera$entityPatch == null || ((Entity)(Object)this).isRemoved()) return;

        BuiltInEventHooks.Entity.REMOVED.postWithListener(
            new EntityRemoveEvent(akythera$entityPatch, reason), akythera$entityPatch.getEntityEventListener()
        );
    }

    /// Lets the brain of this entity feel `pusher` pushing it.
    @Inject(at = @At(value = "HEAD"), method = "push(Lnet/minecraft/world/entity/Entity;)V")
    private void akythera$feelPush(final Entity pusher, final CallbackInfo callbackInfo) {
        BehaviorTreeInstance tree = akythera$entityPatch == null || akythera$entityPatch.isLogicalClient()
            ? null
            : BrainComponent.treeOf((Entity)(Object)this);

        if (tree != null) tree.feelPush(pusher);
    }

    /// Takes gravity off the entity while a montage holds a [NoGravityNotifyState].
    @Inject(at = @At(value = "HEAD"), method = "getGravity()D", cancellable = true)
    private void akythera$noGravityInNotifyState(final CallbackInfoReturnable<Double> callbackInfo) {
        ArmatureComponent armatureComponent = akythera$entityPatch == null ? null : akythera$entityPatch.getArmatureComponent();

        if (armatureComponent == null) return;

        for (AnimationMontagePlayer montagePlayer : armatureComponent.getAnimator().getMontagePlayers()) {
            if (montagePlayer.getActiveNotifyState(NoGravityNotifyState.class) != null) {
                callbackInfo.setReturnValue(0.0D);
                return;
            }
        }
    }

    /// Keeps a [CinemaScene.Actor#ignoreBlockCollision] actor inside the blocks the scene places it in.
    @Inject(at = @At(value = "HEAD"), method = "moveTowardsClosestSpace(DDD)V", cancellable = true)
    private void akythera$stayInBlocksInCinemaScene(final double x, final double y, final double z, final CallbackInfo callbackInfo) {
        CinemaScenePlayback playback = akythera$entityPatch == null ? null : akythera$entityPatch.getCinemaScenePlayback();

        if (playback != null && playback.getActor().ignoreBlockCollision()) callbackInfo.cancel();
    }

    @Override
    public EntityPatch<?> akythera$getEntityPatch() {
        return akythera$entityPatch;
    }

    @Override
    public void akythera$setEntityPatch(final EntityPatch<?> dynamicPatch) {
        akythera$entityPatch = dynamicPatch;
    }
}
