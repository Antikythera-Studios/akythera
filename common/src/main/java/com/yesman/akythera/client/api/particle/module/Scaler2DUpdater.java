package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;

/// Grows a sprite particle's size by `amount` per second. A size never drops below zero.
///
/// @param amount in blocks per second. Zero when unset
public record Scaler2DUpdater(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<Scaler2DUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(Scaler2DUpdater::amount)
            ).apply(instance, Scaler2DUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        AkytheraSpriteParticle sprite = (AkytheraSpriteParticle)particle;
        float amount = new ParticleModuleContext(particle, "scaler_2d").evaluateFloat(this.amount, 0.0F);

        sprite.setSpriteSize(Math.max(0.0F, sprite.getSpriteSize() + amount / 20.0F));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraSpriteParticle.class;
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Scaler2DUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
