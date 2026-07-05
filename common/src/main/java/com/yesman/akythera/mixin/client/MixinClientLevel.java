package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class)
public abstract class MixinClientLevel {
    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "addEntity(Lnet/minecraft/world/entity/Entity;)V"
    )
    private void addEntity(Entity entity, CallbackInfo callbackInfo) {
        if (ModPlatformProvider.get().triggerMixinHook()) {
            // Trigger SpawnEntity in ClientLevel
            AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
                BuiltInEventHooks.Entity.SPAWNED.postWithListener(
                    new EntitySpawnEvent(entityPatch, entity.level(), false),
                    entityPatch.getEntityEventListener()
                );
            });
        }
    }

    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V"
    )
    private void disconnect(Component message, CallbackInfo callbackInfo) {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.post(new RegistryAccessEvent.Unload());
    }
}
