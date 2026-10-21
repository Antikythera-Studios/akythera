package com.yesman.akythera.mixin.common;

import com.yesman.akythera.core.util.EnumerableDataBlockSync;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(value = RegistryDataLoader.class)
public abstract class MixinRegistryDataLoader {
    @Inject(
        at = @At(value = "HEAD"),
        method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
    )
    private static void load(
        final ResourceManager resourceManager,
        final List<HolderLookup.RegistryLookup<?>> contextRegistries,
        final List<RegistryDataLoader.RegistryData<?>> registriesToLoad,
        final Executor executor,
        final CallbackInfoReturnable<CompletableFuture<RegistryAccess.Frozen>> callbackInfo
    ) {
        EnumerableDataBlockSync.loadBeforeRegistries(resourceManager);
    }
}
