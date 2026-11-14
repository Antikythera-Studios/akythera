package com.yesman.akythera.core.cinema;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.cinema.CinemaScenePlayer;
import com.yesman.akythera.client.api.camera.CameraComponent;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;
import java.util.Optional;

/// Actors placed around one root, each playing an [AnimationMontage] from the same moment. Played by
/// [CinemaScenePlayer].
///
/// The scene's space is the model space of its root: origin on the root, -Z forward, +X right. It follows the root
/// while the scene plays.
///
/// @param actors   the actors by role
/// @param duration how long the scene keeps its actors placed, in seconds
public record CinemaScene(Map<String, Actor> actors, float duration) {
    public static final Codec<CinemaScene> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, Actor.CODEC).fieldOf("actors").forGetter(CinemaScene::actors),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("duration").forGetter(CinemaScene::duration)
            )
            .apply(instance, CinemaScene::new)
    );

    /// One role of a scene.
    ///
    /// @param transform             where the actor stands when the scene starts, in the scene's space
    /// @param montage               the montage the actor plays, or empty for an actor the scene only places
    /// @param camera                the actor's [CameraComponent] while the scene plays
    /// @param ignoreBlockCollision  whether the actor is placed through blocks, and never pushed out of them
    /// @param ignoreEntityCollision whether the actor neither pushes nor is pushed by other entities
    public record Actor(
        Transform transform, Optional<ResourceKey<AnimationMontage>> montage, Camera camera,
        boolean ignoreBlockCollision, boolean ignoreEntityCollision
    ) {
        public static final Codec<Actor> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Transform.CODEC.optionalFieldOf("transform", Transform.IDENTITY).forGetter(Actor::transform),
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).optionalFieldOf("montage").forGetter(Actor::montage),
                    Camera.CODEC.optionalFieldOf("camera", Camera.EMPTY).forGetter(Actor::camera),
                    Codec.BOOL.optionalFieldOf("ignore_block_collision", false).forGetter(Actor::ignoreBlockCollision),
                    Codec.BOOL.optionalFieldOf("ignore_entity_collision", false).forGetter(Actor::ignoreEntityCollision)
                )
                .apply(instance, Actor::new)
        );
    }

    /// The camera track of an actor.
    ///
    /// @param keys             where the camera sits, keyed by seconds since the scene started. Empty leaves the camera
    ///                         alone
    /// @param forceThirdPerson whether the local player's view switches to third person while the scene plays
    /// @param followsActor     whether the keys are in the actor's model space. Off, they are in the scene's space
    public record Camera(KeyframeTrack<Transform> keys, boolean forceThirdPerson, boolean followsActor) {
        public static final Camera EMPTY = new Camera(KeyframeTrack.empty(), false, true);

        public static final Codec<Camera> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("keys", KeyframeTrack.empty()).forGetter(Camera::keys),
                    Codec.BOOL.optionalFieldOf("force_third_person", false).forGetter(Camera::forceThirdPerson),
                    Codec.BOOL.optionalFieldOf("follows_actor", true).forGetter(Camera::followsActor)
                )
                .apply(instance, Camera::new)
        );
    }
}
