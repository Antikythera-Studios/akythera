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

/// Gives a particle a velocity around `direction`, in its emitter's space: inside a cone `angleSpread` wide around
/// `direction`, with its speed spread by up to `magnitudeSpread` either way.
///
/// @param direction in blocks per second. Zero when unset
/// @param magnitudeSpread in blocks per second. Zero when unset
/// @param angleSpread the cone's full width, in degrees. Zero when unset
public record PusherInitializer(
    @ModuleParameter(Vec3.class) PureNode direction,
    @ModuleParameter(float.class) PureNode magnitudeSpread,
    @ModuleParameter(float.class) PureNode angleSpread
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<PusherInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("direction").forGetter(PusherInitializer::direction),
                ParticleModuleContext.parameter("magnitude_spread").forGetter(PusherInitializer::magnitudeSpread),
                ParticleModuleContext.parameter("angle_spread").forGetter(PusherInitializer::angleSpread)
            ).apply(instance, PusherInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "pusher");
        Vec3 direction = context.evaluate(this.direction, Vec3.ZERO);
        float magnitudeSpread = context.evaluateFloat(this.magnitudeSpread, 0.0F);
        float angleSpread = context.evaluateFloat(this.angleSpread, 0.0F);
        double length = direction.length();

        if (length < 1.0E-7D) return;

        RandomSource random = particle.getRandom();
        Vec3 axis = direction.scale(1.0D / length);
        Vec3 side = axis.cross(Math.abs(axis.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 up = axis.cross(side);

        // Uniform over the spherical cap around the axis
        double cos = 1.0D - random.nextDouble() * (1.0D - Math.cos(angleSpread * 0.5F * Mth.DEG_TO_RAD));
        double sin = Math.sqrt(Math.max(0.0D, 1.0D - cos * cos));
        double turn = random.nextDouble() * Math.PI * 2.0D;
        Vec3 tilted = axis.scale(cos).add(side.scale(sin * Math.cos(turn))).add(up.scale(sin * Math.sin(turn)));
        double speed = Math.max(0.0D, length + (random.nextDouble() * 2.0D - 1.0D) * magnitudeSpread);

        particle.setVelocity(particle.getEmitter().getWorldTransform().mulDirection(tilted.scale(speed)));
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<PusherInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
