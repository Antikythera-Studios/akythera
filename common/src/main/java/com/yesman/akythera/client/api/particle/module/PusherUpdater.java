package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import net.minecraft.world.phys.Vec3;

/// Moves a particle by `velocity` every tick, in its emitter's space, colliding with blocks when it has physics.
/// Leaves its velocity alone.
///
/// @param velocity in blocks per second. Zero when unset
public record PusherUpdater(
    @ModuleParameter(Vec3.class) PureNode velocity
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<PusherUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("velocity").forGetter(PusherUpdater::velocity)
            ).apply(instance, PusherUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        Vec3 velocity = new ParticleModuleContext(particle, "pusher").evaluate(this.velocity, Vec3.ZERO).scale(1.0D / 20.0D);
        velocity = particle.getEmitter().getWorldTransform().mulDirection(velocity);

        particle.move(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<PusherUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
