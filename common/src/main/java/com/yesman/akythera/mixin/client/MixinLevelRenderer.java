package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.TickEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(
        at = @At(value = "HEAD"),
        method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
    )
    private void extractVisibleEntities(
        Entity entity,
        float partialTickTime,
        CallbackInfoReturnable<EntityRenderState> callbackInfo
    ) {
        // Update the pose before extract
        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            // Get a real frame seconds by multiplying a tick seconds(0.05) to delta ticks (clamped in 0 ~ 1)
            float deltaTime = Minecraft.getInstance().isPaused() ? 0.0F : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() * 0.05F;
            BuiltInEventHooks.Entity.RENDER_TICK.postWithListener(new TickEvent.RenderTick(entityPatch, deltaTime, partialTickTime), entityPatch.getEntityEventListener());
        });
    }
}
