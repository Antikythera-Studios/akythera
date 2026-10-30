package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import net.minecraft.resources.Identifier;

/// A ribbon the emitter's path sweeps: every frame the particle samples edges, each from the emitter's origin to a
/// point `length` blocks out, and draws the edges of the last `trailLifetime` ticks as one strip. Attach the system to
/// a bone socket for a weapon trail. Drawn only where compute shaders are supported.
///
/// An emitter spawns it by a [TrailSpawnRule]. The particle samples until the rule's time span ends, then stays until
/// its last edge expires. [ParticleProperties#lifetime] is unused: the time span is the lifetime.
///
/// @param length        in blocks, from the emitter's origin to the trail's far side along its +Y axis. The emitter's
///                      own rotation turns it
/// @param fadeOut       in ticks, at most `trailLifetime`. An edge with less lifetime left than this is drawn at
///                      `lifetime left / fadeOut` of the color's alpha. 0 never fades
/// @param maxEdgesPerFrame at most this many edges sampled in a frame, and poses sampled for them. Fewer edges when fewer
///                         keep two edges at most `length × edgeSpacing` apart
/// @param edgeSpacing      the longest gap between two edges that needs no more edges between, as a share of `length`.
///                         0 always samples `maxEdgesPerFrame` edges
/// @param trailLifetime    in ticks, how long one edge stays
public record TrailParticleBlueprint(
    ParticleProperties properties,
    float length,
    RGBA color,
    int fadeOut,
    int maxEdgesPerFrame,
    float edgeSpacing,
    int trailLifetime,
    Identifier texture
) implements ParticleBlueprint {
    public static final MapCodec<TrailParticleBlueprint> MAP_CODEC = RecordCodecBuilder.<TrailParticleBlueprint>mapCodec(
        instance ->
            instance.group(
                ParticleProperties.MAP_CODEC.forGetter(TrailParticleBlueprint::properties),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("length", 1.0F).forGetter(TrailParticleBlueprint::length),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(TrailParticleBlueprint::color),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("fade_out", 0).forGetter(TrailParticleBlueprint::fadeOut),
                Codec.intRange(1, 32).optionalFieldOf("max_edges_per_frame", 5).forGetter(TrailParticleBlueprint::maxEdgesPerFrame),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("edge_spacing", 1.0F).forGetter(TrailParticleBlueprint::edgeSpacing),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("trail_lifetime", 6).forGetter(TrailParticleBlueprint::trailLifetime),
                Identifier.CODEC.fieldOf("texture").forGetter(TrailParticleBlueprint::texture)
            ).apply(instance, TrailParticleBlueprint::new)
    ).validate(TrailParticleBlueprint::validate);

    /// Checks the fade out fits in the trail lifetime.
    public DataResult<TrailParticleBlueprint> validate() {
        return fadeOut > trailLifetime
            ? DataResult.error(() -> "Trail fade out " + fadeOut + " exceeds its trail lifetime " + trailLifetime)
            : DataResult.success(this);
    }

    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraTrailParticle(emitter, this);
    }

    @Override
    public MapCodec<TrailParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
