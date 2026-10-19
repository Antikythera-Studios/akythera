package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.client.api.particle.AkytheraParticles;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.WeakHashMap;

/// Plays a particle system that follows a socket of the entity, over the notify state's time window: every emitter
/// with a time span, a trail or spawn rate one, takes the window's length as its time span
/// ([ParticleEmitterBlueprint#withTimeSpan]).
public final class TrailParticleNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<TrailParticleNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            ResourceKey.codec(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY).fieldOf("particle_system").forGetter(TrailParticleNotifyState::particleSystem),
            Codec.STRING.optionalFieldOf("socket", "").forGetter(TrailParticleNotifyState::socket)
        ).apply(inst, TrailParticleNotifyState::new)
    );

    private final ResourceKey<ParticleSystemBlueprint> particleSystem;

    /// The [ParticleAttachment#socketPath] the system follows.
    private final String socket;

    /// The system each owner plays, from [#onBegin] to [#onEnd]. Stopped when the animation is interrupted.
    private final Map<EntityPatch<?>, AkytheraParticleSystem> playing = new WeakHashMap<>();

    public TrailParticleNotifyState(final ResourceKey<ParticleSystemBlueprint> particleSystem, final String socket) {
        super(LogicalSide.CLIENT);

        this.particleSystem = particleSystem;
        this.socket = socket;
    }

    @Override
    public void onBegin(final EntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        ParticleSystemBlueprint blueprint = owner.getOriginal().level().registryAccess()
            .lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY)
            .getValue(particleSystem);

        if (blueprint == null) {
            Akythera.LOGGER.warn("Trail particle notify state names the unknown particle system {}", particleSystem.identifier());
            return;
        }

        // The window's length at 1× play speed. Scale it by the animation's play speed once animations take one
        float span = timeWindow.to() - timeWindow.from();
        AkytheraParticleSystem system = AkytheraParticles.playAttached(
            new ParticleSystemBlueprint(blueprint.emitters().stream().map(emitter -> emitter.withTimeSpan(span)).toList(), blueprint.variables()),
            owner,
            socket
        );

        if (system != null) playing.put(owner, system);
    }

    @Override
    public void onTick(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    /// The system plays on to the end of its time span unless the animation was interrupted.
    @Override
    public void onEnd(final EntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
        AkytheraParticleSystem system = playing.remove(owner);

        if (interrupted && system != null) system.stop();
    }

    public ResourceKey<ParticleSystemBlueprint> particleSystem() {
        return particleSystem;
    }

    public String socket() {
        return socket;
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
