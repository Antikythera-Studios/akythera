package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// Spawns `count` particles at once when the emitter starts, then finishes. Has no time span.
public record InstantSpawnRule(int count) implements EmitterSpawnRule {
    public static final MapCodec<InstantSpawnRule> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(InstantSpawnRule::count)
            ).apply(instance, InstantSpawnRule::new)
    );

    @Override
    public Spawner spawner() {
        return new Spawner() {
            private boolean spawned;

            @Override
            public int tick(final float seconds) {
                if (spawned) return 0;

                spawned = true;

                return count;
            }

            @Override
            public boolean isOver(final float seconds) {
                return spawned;
            }
        };
    }

    @Override
    public float duration() {
        return 0.0F;
    }

    @Override
    public float spanTime(final float seconds) {
        return 0.0F;
    }

    @Override
    public MapCodec<InstantSpawnRule> mapCodec() {
        return MAP_CODEC;
    }
}
