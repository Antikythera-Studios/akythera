package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl {
    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V"
    )
    private void onDisconnect(DisconnectionDetails reason, CallbackInfo callbackInfo) {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.post(new RegistryAccessEvent.Unload());
    }
}
