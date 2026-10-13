package com.yesman.akythera.mixin.common;

import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Runs [AkytheraRegistries.DataPack#getValidator] on a loaded data pack registry. Neither mod loader takes a
/// [RegistryValidator] for a modded registry, so this is the only place one runs.
@Mixin(value = RegistryLoadTask.class)
public abstract class MixinRegistryLoadTask<T> {
    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @Inject(at = @At(value = "HEAD"), method = "validateRegistry", cancellable = true)
    private void validateRegistry(final Map<ResourceKey<?>, Exception> loadingErrors, final CallbackInfoReturnable<Optional<Registry<T>>> callbackInfo) {
        RegistryValidator<T> validator = AkytheraRegistries.DataPack.getValidator(registry.key());

        if (validator == null) return;

        Map<ResourceKey<?>, Exception> errors = new HashMap<>();
        validator.validate(registry, errors);

        if (errors.isEmpty()) return;

        loadingErrors.putAll(errors);
        callbackInfo.setReturnValue(Optional.empty());
    }
}
