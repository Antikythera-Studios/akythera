package com.yesman.akythera.mixin.common;

import com.mojang.serialization.Lifecycle;
import com.yesman.akythera.core.Akythera;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MappedRegistry.class)
public abstract class MixinMappedRegistry<T> {
    @Shadow
    @Final
    private ResourceKey<? extends Registry<T>> key;

    @ModifyVariable(
        at = @At(value = "HEAD"),
        method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
        argsOnly = true
    )
    private RegistrationInfo register(final RegistrationInfo registrationInfo) {
        return key.identifier().getNamespace().equals(Akythera.MODID)
            ? new RegistrationInfo(registrationInfo.knownPackInfo(), Lifecycle.stable())
            : registrationInfo;
    }
}
