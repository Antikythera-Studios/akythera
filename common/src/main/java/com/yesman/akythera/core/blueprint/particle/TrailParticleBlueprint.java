package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import net.minecraft.resources.Identifier;

/// A ribbon the emitter's path sweeps: every tick the particle samples an edge from the emitter's origin to a point
/// `length` blocks out, and draws the edges of the last `trailLifetime` ticks as one curved strip. Attach the system to
/// a bone socket for a weapon trail. Drawn only where compute shaders are supported.
///
/// An emitter spawns it by a [TrailSpawnRule]. The particle samples until the rule's time span ends, then stays until
/// its last edge expires. [ParticleProperties#lifetime] is unused: the time span is the lifetime.
///
/// @param length        in blocks, from the emitter's origin to the trail's far side along its +Y axis. The emitter's
///                      own rotation turns it
/// @param fadeOut       the share of the time span after which the trail fades out, 0 to 1. Alpha goes from the
///                      color's own to 0 between `fadeOut × time span` and the time span's end. 1 never fades
/// @param interpolation quads drawn per sampled edge
/// @param trailLifetime in ticks, how long one sampled edge stays
public record TrailParticleBlueprint(
    ParticleProperties properties,
    float length,
    RGBA color,
    float fadeOut,
    int interpolation,
    int trailLifetime,
    Identifier texture
) implements ParticleBlueprint {
    public static final MapCodec<TrailParticleBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleProperties.MAP_CODEC.forGetter(TrailParticleBlueprint::properties),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("length", 1.0F).forGetter(TrailParticleBlueprint::length),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(TrailParticleBlueprint::color),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("fade_out", 1.0F).forGetter(TrailParticleBlueprint::fadeOut),
                Codec.intRange(1, 32).optionalFieldOf("interpolation", 4).forGetter(TrailParticleBlueprint::interpolation),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("trail_lifetime", 6).forGetter(TrailParticleBlueprint::trailLifetime),
                Identifier.CODEC.fieldOf("texture").forGetter(TrailParticleBlueprint::texture)
            ).apply(instance, TrailParticleBlueprint::new)
    );

    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraTrailParticle(emitter, this);
    }

    @Override
    public MapCodec<TrailParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
