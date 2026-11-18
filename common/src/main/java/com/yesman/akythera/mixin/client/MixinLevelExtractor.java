package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.TickEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.world.BlockFractures;
import com.yesman.akythera.mixinaccessor.client.ClientLevelAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {
    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

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

    /// Draws the level's [BlockFractures] with the particles, held still while the game is frozen.
    @Inject(at = @At("TAIL"), method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V")
    private void akythera$extractBlockFractures(final DeltaTracker deltaTracker, final Camera camera, final float deltaPartialTick, final CallbackInfo callbackInfo) {
        if (level == null) return;

        BlockFractures fractures = ((ClientLevelAccessor)level).akythera$getBlockFractures();

        if (!fractures.isEmpty()) levelRenderState.particlesRenderState.add(fractures.extractRenderState(camera.getCullFrustum(), deltaTracker.getGameTimeDeltaPartialTick(false)));
    }
}
