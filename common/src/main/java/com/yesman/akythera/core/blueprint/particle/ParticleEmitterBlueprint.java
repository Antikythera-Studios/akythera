package com.yesman.akythera.core.blueprint.particle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.stream.Stream;

/// One emitter of a particle system: which particle it spawns, when, where, and the modules its particles run.
///
/// @param particle               a particle piece, or a vanilla particle type spawned as
///                               [SpriteParticleBlueprint#ofVanilla]
/// @param start                  when the emitter starts, in seconds since the system started
/// @param hideParticlesOnFinish  whether the emitter removes the particles it spawned when it finishes
/// @param pivot                  the space the emitter's transform is in, in its system's space, while `pivotKeys` is
///                               empty. Turning it swings the emitter around it
/// @param transform              the emitter's transform in the pivot's space, while `transformKeys` is empty
/// @param pivotKeys              the pivot over the emitter's time span
/// @param transformKeys          the emitter's transform over the emitter's time span
/// @param initializers           run once on each particle right after it spawns, in order. One of each module
/// @param updaters               run on each particle every tick, in order. One of each module
public record ParticleEmitterBlueprint(
    String name,
    Either<ResourceKey<ParticleBlueprint>, Identifier> particle,
    float start,
    EmitterSpawnRule spawn,
    boolean hideParticlesOnFinish,
    Transform pivot,
    Transform transform,
    KeyframeTrack<Transform> pivotKeys,
    KeyframeTrack<Transform> transformKeys,
    List<ParticleInitializerBlueprint> initializers,
    List<ParticleUpdaterBlueprint> updaters
) {
    public static final Codec<ParticleEmitterBlueprint> CODEC = RecordCodecBuilder.<ParticleEmitterBlueprint>create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter(ParticleEmitterBlueprint::name),
                Codec.either(ResourceKey.codec(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY), SpriteParticleBlueprint.VANILLA_PARTICLE).fieldOf("particle").forGetter(ParticleEmitterBlueprint::particle),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("start", 0.0F).forGetter(ParticleEmitterBlueprint::start),
                EmitterSpawnRule.CODEC.fieldOf("spawn").forGetter(ParticleEmitterBlueprint::spawn),
                Codec.BOOL.optionalFieldOf("hide_particles_on_finish", false).forGetter(ParticleEmitterBlueprint::hideParticlesOnFinish),
                Transform.CODEC.optionalFieldOf("pivot", Transform.IDENTITY).forGetter(ParticleEmitterBlueprint::pivot),
                Transform.CODEC.optionalFieldOf("transform", Transform.IDENTITY).forGetter(ParticleEmitterBlueprint::transform),
                KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("pivot_keys", KeyframeTrack.empty()).forGetter(ParticleEmitterBlueprint::pivotKeys),
                KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("transform_keys", KeyframeTrack.empty()).forGetter(ParticleEmitterBlueprint::transformKeys),
                ParticleInitializerBlueprint.CODEC.listOf().optionalFieldOf("initializers", List.of()).forGetter(ParticleEmitterBlueprint::initializers),
                ParticleUpdaterBlueprint.CODEC.listOf().optionalFieldOf("updaters", List.of()).forGetter(ParticleEmitterBlueprint::updaters)
            ).apply(instance, ParticleEmitterBlueprint::new)
    ).validate(ParticleEmitterBlueprint::validate);

    /// Checks one of each module per list, and keyframes inside the spawn rule's time span.
    public DataResult<ParticleEmitterBlueprint> validate() {
        if (initializers.stream().map(ParticleInitializerBlueprint::moduleType).distinct().count() != initializers.size()
            || updaters.stream().map(ParticleUpdaterBlueprint::moduleType).distinct().count() != updaters.size()
        ) {
            return DataResult.error(() -> "Emitter " + name + " holds the same module twice in one list");
        }

        float duration = spawn.duration();
        boolean outside = Stream.concat(pivotKeys.keys().stream(), transformKeys.keys().stream())
            .anyMatch(key -> duration <= 0.0F || key.time() > duration);

        if (outside) {
            return DataResult.error(() -> duration <= 0.0F
                ? "Emitter " + name + " has keyframes, but its spawn rule has no time span"
                : "Emitter " + name + " has a keyframe past its time span of " + duration + " seconds");
        }

        return DataResult.success(this);
    }

    /// The emitter's transform in its system's space at `spanTime` seconds into its time span: the emitter's transform
    /// placed in the pivot's space.
    public Transform placementAt(final float spanTime) {
        Transform pivot = pivotKeys.sample(spanTime, this.pivot, Transform::slerp);
        Transform transform = transformKeys.sample(spanTime, this.transform, Transform::slerp);

        return pivot.mul(transform);
    }
}
