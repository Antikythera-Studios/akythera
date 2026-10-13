package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;

/// Turns a sprite particle's roll by a random angle in a range `amount` wide, centered on the current roll.
///
/// @param amount the range's full width, in degrees. Zero when unset
public record Spinner2DInitializer(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<Spinner2DInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(Spinner2DInitializer::amount)
            ).apply(instance, Spinner2DInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        AkytheraSpriteParticle sprite = (AkytheraSpriteParticle)particle;
        float amount = new ParticleModuleContext(particle, "spinner_2d").evaluateFloat(this.amount, 0.0F);

        sprite.setRoll(sprite.getRoll() + (particle.getRandom().nextFloat() - 0.5F) * amount);
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
    public MapCodec<Spinner2DInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
