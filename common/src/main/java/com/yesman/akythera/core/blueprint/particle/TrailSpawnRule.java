package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;

/// Spawns one particle when the emitter starts, then finishes after a time span of `duration` seconds. The rule every
/// emitter of a [TrailParticleBlueprint] spawns by: the [AkytheraTrailParticle] samples edges until the time span ends.
public record TrailSpawnRule(float duration) implements EmitterSpawnRule {
    public static final MapCodec<TrailSpawnRule> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.floatRange(0.05F, Float.MAX_VALUE).optionalFieldOf("duration", 1.0F).forGetter(TrailSpawnRule::duration)
            ).apply(instance, TrailSpawnRule::new)
    );

    @Override
    public Spawner spawner() {
        return new Spawner() {
            private boolean spawned;

            @Override
            public int tick(final float seconds) {
                if (spawned) return 0;

                spawned = true;

                return 1;
            }

            /// Not before the trail spawned, for an emitter whose first tick is already past its time span.
            @Override
            public boolean isOver(final float seconds) {
                return spawned && seconds >= duration;
            }
        };
    }

    @Override
    public float spanTime(final float seconds) {
        return Math.min(seconds, duration);
    }

    @Override
    public TrailSpawnRule withDuration(final float duration) {
        return new TrailSpawnRule(duration);
    }

    @Override
    public MapCodec<TrailSpawnRule> mapCodec() {
        return MAP_CODEC;
    }
}
