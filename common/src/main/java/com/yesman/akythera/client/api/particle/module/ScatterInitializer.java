package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/// Moves a particle to a random point of the box between `min` and `max`, in its emitter's space.
///
/// @param min the box's lower corner, in blocks. Zero when unset
/// @param max the box's upper corner, in blocks. Zero when unset
public record ScatterInitializer(
    @ModuleParameter(value = Vec3.class, atMost = "max") PureNode min,
    @ModuleParameter(value = Vec3.class, atLeast = "min") PureNode max
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<ScatterInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("min").forGetter(ScatterInitializer::min),
                ParticleModuleContext.parameter("max").forGetter(ScatterInitializer::max)
            ).apply(instance, ScatterInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "scatter");
        Vec3 min = context.evaluate(this.min, Vec3.ZERO);
        Vec3 max = context.evaluate(this.max, Vec3.ZERO);
        RandomSource random = particle.getRandom();
        Vec3 offset = new Vec3(
            Mth.lerp(random.nextDouble(), min.x, max.x),
            Mth.lerp(random.nextDouble(), min.y, max.y),
            Mth.lerp(random.nextDouble(), min.z, max.z)
        );

        particle.setPosition(particle.getEmitter().getWorldTransform().mul(offset));
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<ScatterInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
