package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/// Changes a mesh particle's scale on each axis by a random amount up to `spread`'s value on that axis, either way.
/// A scale never drops below zero.
///
/// @param spread Zero when unset
public record Scaler3DInitializer(
    @ModuleParameter(Vec3.class) PureNode spread
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<Scaler3DInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("spread").forGetter(Scaler3DInitializer::spread)
            ).apply(instance, Scaler3DInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        AkytheraMeshParticle mesh = (AkytheraMeshParticle)particle;
        Vec3 spread = new ParticleModuleContext(particle, "scaler_3d").evaluate(this.spread, Vec3.ZERO);
        RandomSource random = particle.getRandom();
        Vec3 scale = mesh.getScale().add(
            (random.nextDouble() * 2.0D - 1.0D) * spread.x,
            (random.nextDouble() * 2.0D - 1.0D) * spread.y,
            (random.nextDouble() * 2.0D - 1.0D) * spread.z
        );

        mesh.setScale(new Vec3(Math.max(0.0D, scale.x), Math.max(0.0D, scale.y), Math.max(0.0D, scale.z)));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraMeshParticle.class;
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Scaler3DInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
