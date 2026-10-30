package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.particle.AkytheraMeshParticleGroup;
import com.yesman.akythera.client.api.particle.AkytheraParticleRenderTypes;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticleGroup;
import com.yesman.akythera.mixinaccessor.client.ParticleEngineAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine implements ParticleEngineAccessor {
    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    /// Whether [ParticleEngine#tick] runs.
    ///
    /// A particle added between ticks waits in vanilla's queue until the next tick has ticked every group, so it misses
    /// that tick and draws nothing until then. A trail spawned by an animation frame would lose the frames it was spawned
    /// for, and its age would lag a tick behind the system's start time. [#akythera$addNow] adds such a particle at
    /// once, and keeps the queue while the engine ticks, where vanilla's order is right.
    @Unique
    private boolean akythera$ticking;

    @Shadow
    public abstract void add(Particle particle);

    @Shadow
    private ParticleGroup<?> createParticleGroup(final ParticleRenderType type) {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void akythera$tickHead(final CallbackInfo callback) {
        akythera$ticking = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void akythera$tickReturn(final CallbackInfo callback) {
        akythera$ticking = false;
    }

    @Override
    public void akythera$addNow(final Particle particle) {
        if (akythera$ticking || particle.getParticleLimit().isPresent()) {
            add(particle);
        } else {
            particles.computeIfAbsent(particle.getGroup(), this::createParticleGroup).add(particle);
        }
    }

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
