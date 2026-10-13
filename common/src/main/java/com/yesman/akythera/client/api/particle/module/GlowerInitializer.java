package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import net.minecraft.util.Mth;

/// Sets a particle's self-brightness to a random value between `min` and `max`.
///
/// @param min 0 to 1. The particle's own brightness when unset
/// @param max 0 to 1. `min` when unset
public record GlowerInitializer(
    @ModuleParameter(value = float.class, atMost = "max") PureNode min,
    @ModuleParameter(value = float.class, atLeast = "min") PureNode max
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<GlowerInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("min").forGetter(GlowerInitializer::min),
                ParticleModuleContext.parameter("max").forGetter(GlowerInitializer::max)
            ).apply(instance, GlowerInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "glower");
        float min = context.evaluateFloat(this.min, particle.getBrightness());
        float max = context.evaluateFloat(this.max, min);

        particle.setBrightness(Mth.clamp(Mth.lerp(particle.getRandom().nextFloat(), min, max), 0.0F, 1.0F));
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<GlowerInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
