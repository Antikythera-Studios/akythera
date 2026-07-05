package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> {
    @Inject(
        at = @At(
            value = "HEAD"
        ),
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V"
    )
    private void extractRenderState(final T entity, final S state, final float partialTick, CallbackInfo callbackInfo) {
        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() != null) {
                ((LivingEntityRenderStateAccessor)state).akythera$setPose(entityPatch.getArmatureComponent().getAnimator().getPose());
                ((LivingEntityRenderStateAccessor)state).akythera$setArmature(entityPatch.getArmatureComponent().getArmature());
            }
        });
    }
}
