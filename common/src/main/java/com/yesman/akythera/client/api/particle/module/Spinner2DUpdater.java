package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;

/// Turns a sprite particle's roll by `amount` per second.
///
/// @param amount in degrees per second. Zero when unset
public record Spinner2DUpdater(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<Spinner2DUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(Spinner2DUpdater::amount)
            ).apply(instance, Spinner2DUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        AkytheraSpriteParticle sprite = (AkytheraSpriteParticle)particle;
        float amount = new ParticleModuleContext(particle, "spinner_2d").evaluateFloat(this.amount, 0.0F);

        sprite.setRoll(sprite.getRoll() + amount / 20.0F);
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
    public MapCodec<Spinner2DUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
