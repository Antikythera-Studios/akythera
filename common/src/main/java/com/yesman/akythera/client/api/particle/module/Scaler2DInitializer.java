package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;

/// Changes a sprite particle's size by a random amount up to `spread` either way. A size never drops below zero.
///
/// @param spread in blocks. Zero when unset
public record Scaler2DInitializer(
    @ModuleParameter(float.class) PureNode spread
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<Scaler2DInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("spread").forGetter(Scaler2DInitializer::spread)
            ).apply(instance, Scaler2DInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        AkytheraSpriteParticle sprite = (AkytheraSpriteParticle)particle;
        float spread = new ParticleModuleContext(particle, "scaler_2d").evaluateFloat(this.spread, 0.0F);

        sprite.setSpriteSize(Math.max(0.0F, sprite.getSpriteSize() + (particle.getRandom().nextFloat() * 2.0F - 1.0F) * spread));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraSpriteParticle.class;
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Scaler2DInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
