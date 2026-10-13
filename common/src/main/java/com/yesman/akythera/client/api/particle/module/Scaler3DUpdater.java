package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import net.minecraft.world.phys.Vec3;

/// Grows a mesh particle's scale by `amount` per second on each axis. A scale never drops below zero.
///
/// @param amount per second. Zero when unset
public record Scaler3DUpdater(
    @ModuleParameter(Vec3.class) PureNode amount
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<Scaler3DUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(Scaler3DUpdater::amount)
            ).apply(instance, Scaler3DUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        AkytheraMeshParticle mesh = (AkytheraMeshParticle)particle;
        Vec3 amount = new ParticleModuleContext(particle, "scaler_3d").evaluate(this.amount, Vec3.ZERO);
        Vec3 scale = mesh.getScale().add(amount.scale(1.0D / 20.0D));

        mesh.setScale(new Vec3(Math.max(0.0D, scale.x), Math.max(0.0D, scale.y), Math.max(0.0D, scale.z)));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraMeshParticle.class;
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Scaler3DUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
