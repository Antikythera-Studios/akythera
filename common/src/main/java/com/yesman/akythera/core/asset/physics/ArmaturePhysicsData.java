package com.yesman.akythera.core.asset.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// The physical body of each bone of an [Armature]: what cloth collides against, and what a rag doll
/// simulates.
///
/// The asset sits at the armature's own path under the armature physics directory, so the armature
/// [#bodies] is keyed by is the file's identity rather than a field inside it.
///
/// @param bodies bone name to that bone's body; a bone with no body is absent
public record ArmaturePhysicsData(Map<String, BoneBody> bodies) {
    /// The shapes a body may carry.
    public static final Set<ColliderType> BODY_SHAPES = Set.of(
        AkytheraColliderType.SPHERE,
        AkytheraColliderType.CAPSULE,
        AkytheraColliderType.ORIENTED_BOX
    );

    public static final Codec<ArmaturePhysicsData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, BoneBody.CODEC).optionalFieldOf("bodies", Map.of())
                    .forGetter(ArmaturePhysicsData::bodies)
            ).apply(instance, ArmaturePhysicsData::new)
    );

    /// One bone's rigid body.
    ///
    /// @param colliders shapes in the bone's space, each pivoted by its own transform, each of [#BODY_SHAPES]
    public record BoneBody(List<Collider> colliders) {
        public static final Codec<BoneBody> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Collider.MAP_CODEC.codec()
                        .validate(collider -> BODY_SHAPES.contains(collider.type())
                            ? DataResult.success(collider)
                            : DataResult.error(() -> "A bone body cannot carry a " + collider.type() + " collider"))
                        .listOf()
                        .optionalFieldOf("colliders", List.of())
                        .forGetter(BoneBody::colliders)
                ).apply(instance, BoneBody::new)
        );
    }
}
