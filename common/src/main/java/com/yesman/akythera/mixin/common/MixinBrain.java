package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class MixinBrain {
    /// Stops the memories, sensors and behaviors of a vanilla brain whose body runs a [BrainComponent].
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void akythera$muteForBrainComponent(final ServerLevel level, final LivingEntity body, final CallbackInfo callbackInfo) {
        if (((EntityAccessor)body).akythera$getEntityPatch() instanceof EntityPatch<?> patch && patch.getComponent(BrainComponent.class) != null) {
            callbackInfo.cancel();
        }
    }
}
