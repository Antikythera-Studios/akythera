package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = ClientConfigurationPacketListenerImpl.class)
public abstract class MixinClientConfigurationPacketListenerImpl {
    @Inject(
        at = @At(
            value = "TAIL"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ClientboundFinishConfigurationPacket;)V"
    )
    private void handleConfigurationFinished(
        ClientboundFinishConfigurationPacket packet, CallbackInfo callbackInfo,
        // Local variable captures
        RegistryAccess.Frozen registries
    ) {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_LOAD.post(new RegistryAccessEvent.Load(registries));
    }
}
