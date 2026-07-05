package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.TickEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
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

    @Override
    public EntityPatch<?> akythera$getEntityPatch() {
        return akythera$entityPatch;
    }

    @Override
    public void akythera$setEntityPatch(final EntityPatch<?> dynamicPatch) {
        akythera$entityPatch = dynamicPatch;
    }
}
