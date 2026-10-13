package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.util.function.Function;

/// When an emitter spawns its particles. Dispatched on `type` through
/// [AkytheraRegistries.Frozen#EMITTER_SPAWN_RULE_TYPES].
public interface EmitterSpawnRule {
    Codec<EmitterSpawnRule> CODEC = AkytheraRegistries.Frozen.EMITTER_SPAWN_RULE_TYPES
        .byNameCodec()
        .dispatch(EmitterSpawnRule::mapCodec, Function.identity());

    /// A fresh spawner for one play of the emitter.
    Spawner spawner();

    /// The time span's length, in seconds. Zero for a rule with no time span, whose emitter takes no keyframes.
    float duration();

    /// Where `seconds` since the emitter started falls in the time span, which is what the emitter's keyframes are
    /// sampled at.
    float spanTime(float seconds);

    MapCodec<? extends EmitterSpawnRule> mapCodec();

    /// The playback state of one emitter's spawn rule.
    interface Spawner {
        /// The particles to spawn this tick, `seconds` since the emitter started.
        int tick(float seconds);

        /// Whether the rule spawns nothing more from `seconds` since the emitter started, which finishes the emitter.
        boolean isOver(float seconds);
    }
}
