package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.mixin.client.MixinClientConfigurationPacketListenerImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Posts [RegistryAccessEvent.Load] on the server, which
/// [MixinClientConfigurationPacketListenerImpl] only does for a client receiving the registry
/// packet.
///
/// Without this a dedicated server never fills [StaticRegistryAccess], and the first entity tick
/// that resolves an [ItemPatch] throws.
@Mixin(value = MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();

    @Inject(at = @At(value = "HEAD"), method = "loadLevel()V")
    private void loadLevel(CallbackInfo callbackInfo) {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_LOAD.post(new RegistryAccessEvent.Load(registryAccess()));
    }
}
