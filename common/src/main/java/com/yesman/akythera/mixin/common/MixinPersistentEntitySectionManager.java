package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntitySectionManager {
    @Inject(
        at = @At(
            value = "RETURN"
        ),
        method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z"
    )
    private void addEntity(final EntityAccess entityAccess, final boolean loaded, CallbackInfoReturnable<Boolean> callbackInfo) {
        // callbackInfo.getReturnValue() == true when entity successfully spawned in a world
        if (ModPlatformProvider.get().triggerMixinHook() && entityAccess instanceof Entity entity && callbackInfo.getReturnValue()) {
            // Trigger SpawnEntity in ServerLevel
            AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
                BuiltInEventHooks.Entity.SPAWNED.postWithListener(
                    new EntitySpawnEvent(entityPatch, entity.level(), loaded),
                    entityPatch.getEntityEventListener()
                );
            });
        }
    }
}
