package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.particle.AkytheraMeshParticleGroup;
import com.yesman.akythera.client.api.particle.AkytheraParticleRenderTypes;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticleGroup;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {
    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    /// Builds the groups of [AkytheraParticleRenderTypes], which vanilla would otherwise build as quad groups.
    @Inject(method = "createParticleGroup", at = @At("HEAD"), cancellable = true)
    private void akythera$createParticleGroup(final ParticleRenderType type, final CallbackInfoReturnable<ParticleGroup<?>> callback) {
        if (type == AkytheraParticleRenderTypes.MESH) callback.setReturnValue(new AkytheraMeshParticleGroup((ParticleEngine)(Object)this));
        if (type == AkytheraParticleRenderTypes.TRAIL) callback.setReturnValue(new AkytheraTrailParticleGroup((ParticleEngine)(Object)this));
    }

    /// Extracts the groups of [AkytheraParticleRenderTypes] after vanilla's, in [AkytheraParticleRenderTypes#RENDER_ORDER].
    @Inject(method = "extract", at = @At("TAIL"))
    private void akythera$extract(
        final ParticlesRenderState particlesRenderState, final Frustum frustum, final Camera camera, final float partialTickTime, final CallbackInfo callback
    ) {
        for (ParticleRenderType type : AkytheraParticleRenderTypes.RENDER_ORDER) {
            ParticleGroup<?> group = particles.get(type);

            if (group != null && !group.isEmpty()) particlesRenderState.add(group.extractRenderState(frustum, camera, partialTickTime));
        }
    }
}
