package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;

/// Grows a trail's length by `amount` per second. Edges sampled before keep their length. A length never drops below
/// zero.
///
/// @param amount in blocks per second. Zero when unset
public record TrailLengthUpdater(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<TrailLengthUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(TrailLengthUpdater::amount)
            ).apply(instance, TrailLengthUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        AkytheraTrailParticle trail = (AkytheraTrailParticle)particle;
        float amount = new ParticleModuleContext(particle, "trail_length").evaluateFloat(this.amount, 0.0F);

        trail.setTrailLength(Math.max(0.0F, trail.getTrailLength() + amount / 20.0F));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraTrailParticle.class;
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<TrailLengthUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
