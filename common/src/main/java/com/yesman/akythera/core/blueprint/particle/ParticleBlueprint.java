package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.util.function.Function;

/// How one particle looks and moves, spawned by an [AkytheraParticleEmitter] at its position. Loaded from data packs
/// and synced on join. Dispatched on `type` through [AkytheraRegistries.Frozen#PARTICLE_TYPES].
@ScriptType(autoRegister = false)
public interface ParticleBlueprint extends Blueprint<AkytheraParticle, AkytheraParticleEmitter> {
    Codec<ParticleBlueprint> CODEC = AkytheraRegistries.Frozen.PARTICLE_TYPES
        .byNameCodec()
        .dispatch(ParticleBlueprint::mapCodec, Function.identity());

    ParticleProperties properties();

    @Override
    MapCodec<? extends ParticleBlueprint> mapCodec();
}
