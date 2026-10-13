package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import net.minecraft.util.Mth;

/// Changes a particle's self-brightness by `amount` per second, kept within 0 to 1.
///
/// @param amount per second. Zero when unset
public record GlowerUpdater(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<GlowerUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(GlowerUpdater::amount)
            ).apply(instance, GlowerUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        float amount = new ParticleModuleContext(particle, "glower").evaluateFloat(this.amount, 0.0F);

        particle.setBrightness(Mth.clamp(particle.getBrightness() + amount / 20.0F, 0.0F, 1.0F));
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<GlowerUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
