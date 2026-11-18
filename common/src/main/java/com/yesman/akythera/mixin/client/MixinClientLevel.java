package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.world.BlockFractures;
import com.yesman.akythera.mixinaccessor.client.ClientLevelAccessor;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = ClientLevel.class)
public abstract class MixinClientLevel implements ClientLevelAccessor {
    @Unique
    private final BlockFractures akythera$blockFractures = new BlockFractures((ClientLevel)(Object)this);

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

    @Inject(at = @At("TAIL"), method = "tick(Ljava/util/function/BooleanSupplier;)V")
    private void akythera$tickBlockFractures(final BooleanSupplier haveTime, final CallbackInfo callbackInfo) {
        if (((ClientLevel)(Object)this).tickRateManager().runsNormally()) akythera$blockFractures.tick();
    }

    @Override
    public BlockFractures akythera$getBlockFractures() {
        return akythera$blockFractures;
    }
}
