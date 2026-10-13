package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// Spawns `rate` particles per second over a time span of `duration` seconds. Fractions carry over to the next tick.
///
/// @param loop whether the time span restarts when it ends, until the system is stopped
public record SpawnRateRule(float rate, float duration, boolean loop) implements EmitterSpawnRule {
    public static final MapCodec<SpawnRateRule> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("rate").forGetter(SpawnRateRule::rate),
                Codec.floatRange(0.05F, Float.MAX_VALUE).optionalFieldOf("duration", 1.0F).forGetter(SpawnRateRule::duration),
                Codec.BOOL.optionalFieldOf("loop", false).forGetter(SpawnRateRule::loop)
            ).apply(instance, SpawnRateRule::new)
    );

    @Override
    public Spawner spawner() {
        return new Spawner() {
            /// Particles owed but not spawned yet, below one.
            private float debt;

            @Override
            public int tick(final float seconds) {
                debt += rate / 20.0F;
                int owed = (int)debt;
                debt -= owed;

                return owed;
            }

            @Override
            public boolean isOver(final float seconds) {
                return !loop && seconds >= duration;
            }
        };
    }

    @Override
    public float spanTime(final float seconds) {
        return loop ? seconds % duration : Math.min(seconds, duration);
    }

    @Override
    public MapCodec<SpawnRateRule> mapCodec() {
        return MAP_CODEC;
    }
}
