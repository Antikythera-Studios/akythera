package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import net.minecraft.util.Mth;

/// Moves a particle's color toward `destColor` by `delta` of the remaining difference every tick.
///
/// @param destColor White when unset
/// @param delta 0 to 1, per tick. Zero when unset
public record PainterUpdater(
    @ModuleParameter(RGBA.class) PureNode destColor,
    @ModuleParameter(float.class) PureNode delta
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<PainterUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("dest_color").forGetter(PainterUpdater::destColor),
                ParticleModuleContext.parameter("delta").forGetter(PainterUpdater::delta)
            ).apply(instance, PainterUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "painter");
        RGBA dest = context.evaluate(this.destColor, new RGBA(1.0F, 1.0F, 1.0F));
        float delta = Mth.clamp(context.evaluateFloat(this.delta, 0.0F), 0.0F, 1.0F);
        RGBA color = particle.getColor();

        particle.setColor(new RGBA(
            Mth.lerp(delta, color.r(), dest.r()),
            Mth.lerp(delta, color.g(), dest.g()),
            Mth.lerp(delta, color.b(), dest.b()),
            Mth.lerp(delta, color.a(), dest.a())
        ));
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<PainterUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
