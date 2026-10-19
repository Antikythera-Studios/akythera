package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;

/// Changes a trail's length by a random amount up to `spread` either way. A length never drops below zero.
///
/// @param spread in blocks. Zero when unset
public record TrailLengthInitializer(
    @ModuleParameter(float.class) PureNode spread
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<TrailLengthInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("spread").forGetter(TrailLengthInitializer::spread)
            ).apply(instance, TrailLengthInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        AkytheraTrailParticle trail = (AkytheraTrailParticle)particle;
        float spread = new ParticleModuleContext(particle, "trail_length").evaluateFloat(this.spread, 0.0F);

        trail.setTrailLength(Math.max(0.0F, trail.getTrailLength() + (particle.getRandom().nextFloat() * 2.0F - 1.0F) * spread));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraTrailParticle.class;
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<TrailLengthInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
