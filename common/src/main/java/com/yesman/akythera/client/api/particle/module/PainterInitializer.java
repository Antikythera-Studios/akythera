package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import net.minecraft.util.Mth;

/// Colors a particle at one random point of the gradient from `from` to `to`.
///
/// @param from White when unset
/// @param to `from` when unset
public record PainterInitializer(
    @ModuleParameter(RGBA.class) PureNode from,
    @ModuleParameter(RGBA.class) PureNode to
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<PainterInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("from").forGetter(PainterInitializer::from),
                ParticleModuleContext.parameter("to").forGetter(PainterInitializer::to)
            ).apply(instance, PainterInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "painter");
        RGBA from = context.evaluate(this.from, new RGBA(1.0F, 1.0F, 1.0F));
        RGBA to = context.evaluate(this.to, from);
        float delta = particle.getRandom().nextFloat();

        particle.setColor(new RGBA(
            Mth.lerp(delta, from.r(), to.r()),
            Mth.lerp(delta, from.g(), to.g()),
            Mth.lerp(delta, from.b(), to.b()),
            Mth.lerp(delta, from.a(), to.a())
        ));
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<PainterInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
