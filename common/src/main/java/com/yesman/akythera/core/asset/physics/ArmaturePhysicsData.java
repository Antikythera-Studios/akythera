package com.yesman.akythera.core.asset.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// The physical body of each bone of an [Armature]: what cloth collides against, and what a rag doll
/// simulates.
///
/// The asset sits at the armature's own path under the armature physics directory, so the armature
/// [#bodies] is keyed by is the file's identity rather than a field inside it.
///
/// @param bodies      bone name to that bone's body; a bone with no body is absent
/// @param constraints bone name to the limits that bone turns within against its parent; a bone with no
///                    constraint is absent and turns freely
/// @param simulation  the rag doll's solver settings
public record ArmaturePhysicsData(Map<String, BoneBody> bodies, Map<String, BoneConstraint> constraints, RagdollParameters simulation) {
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
                    .forGetter(ArmaturePhysicsData::bodies),
                Codec.unboundedMap(Codec.STRING, BoneConstraint.CODEC).optionalFieldOf("constraints", Map.of())
                    .forGetter(ArmaturePhysicsData::constraints),
                RagdollParameters.CODEC.optionalFieldOf("simulation", RagdollParameters.DEFAULT)
                    .forGetter(ArmaturePhysicsData::simulation)
            ).apply(instance, ArmaturePhysicsData::new)
    );

    /// One bone's rigid body.
    ///
    /// @param colliders      shapes in the bone's space, each pivoted by its own transform, each of [#BODY_SHAPES]
    /// @param mass           the body's mass, spread over its colliders by their volume
    /// @param linearDamping  velocity lost per second, as a fraction
    /// @param angularDamping angular velocity lost per second, as a fraction
    public record BoneBody(List<Collider> colliders, float mass, float linearDamping, float angularDamping) {
        public static final float DEFAULT_MASS = 1.0F;
        public static final float DEFAULT_LINEAR_DAMPING = 0.1F;
        public static final float DEFAULT_ANGULAR_DAMPING = 0.5F;

        public static final Codec<BoneBody> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Collider.MAP_CODEC.codec()
                        .validate(collider -> BODY_SHAPES.contains(collider.type())
                            ? DataResult.success(collider)
                            : DataResult.error(() -> "A bone body cannot carry a " + collider.type() + " collider"))
                        .listOf()
                        .optionalFieldOf("colliders", List.of())
                        .forGetter(BoneBody::colliders),
                    Codec.floatRange(0.001F, Float.MAX_VALUE).optionalFieldOf("mass", DEFAULT_MASS).forGetter(BoneBody::mass),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("linear_damping", DEFAULT_LINEAR_DAMPING).forGetter(BoneBody::linearDamping),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("angular_damping", DEFAULT_ANGULAR_DAMPING).forGetter(BoneBody::angularDamping)
                ).apply(instance, BoneBody::new)
        );
    }

    /// How far a bone turns against its parent, read off the bone's own pose rotation.
    ///
    /// The pose rotation splits into a twist around the bone's Y axis, the axis it points along, and a
    /// swing that tips Y away. The swing is limited by its X and Z parts, which is the area
    /// [AngularLimit]s of [#x] and [#z] draw on a sphere around the bone's head.
    ///
    /// @param x            swing around the bone's X axis, in degrees
    /// @param y            twist around the bone's Y axis, in degrees
    /// @param z            swing around the bone's Z axis, in degrees
    /// @param halfBendBone a bone that turns by half of this bone's bend around X, null for none
    /// @param maxStretch   how far [#halfBendBone] grows across the bend to keep the skin's volume, as a ratio (≥ 1)
    public record BoneConstraint(AngularLimit x, AngularLimit y, AngularLimit z, @Nullable String halfBendBone, float maxStretch) {
        public static final BoneConstraint FREE = new BoneConstraint(AngularLimit.FREE, AngularLimit.FREE, AngularLimit.FREE, null, 1.0F);

        public static final Codec<BoneConstraint> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AngularLimit.CODEC.optionalFieldOf("x", AngularLimit.FREE).forGetter(BoneConstraint::x),
                    AngularLimit.CODEC.optionalFieldOf("y", AngularLimit.FREE).forGetter(BoneConstraint::y),
                    AngularLimit.CODEC.optionalFieldOf("z", AngularLimit.FREE).forGetter(BoneConstraint::z),
                    Codec.STRING.optionalFieldOf("half_bend_bone").forGetter(ParseUtils.provideOptional(BoneConstraint::halfBendBone)),
                    Codec.floatRange(1.0F, Float.MAX_VALUE).optionalFieldOf("max_stretch", 1.0F).forGetter(BoneConstraint::maxStretch)
                ).apply(instance, (x, y, z, halfBendBone, maxStretch) -> new BoneConstraint(x, y, z, halfBendBone.orElse(null), maxStretch))
        );

        /// Whether the rag doll is kept inside any of the three limits.
        public boolean limitsRotation() {
            return !x.isFree() || !y.isFree() || !z.isFree();
        }
    }

    /// A range of angles, in degrees within [-180, 180].
    public record AngularLimit(float min, float max) {
        public static final AngularLimit FREE = new AngularLimit(-180.0F, 180.0F);

        public static final Codec<AngularLimit> CODEC = RecordCodecBuilder.<AngularLimit>create(
            instance ->
                instance.group(
                    Codec.floatRange(-180.0F, 180.0F).fieldOf("min").forGetter(AngularLimit::min),
                    Codec.floatRange(-180.0F, 180.0F).fieldOf("max").forGetter(AngularLimit::max)
                ).apply(instance, AngularLimit::new)
        ).validate(limit -> limit.min() <= limit.max()
            ? DataResult.success(limit)
            : DataResult.error(() -> "Angular limit min " + limit.min() + " exceeds max " + limit.max()));

        public boolean isFree() {
            return min <= -180.0F && max >= 180.0F;
        }
    }

    /// The solver settings of a [RagdollSimulation]. Lengths are in blocks, times in seconds.
    ///
    /// @param substeps      solver substeps per game tick, 1–32
    /// @param gravityScale  multiplier on 9.81 blocks/s² downward
    /// @param friction      how much of a body's sliding a terrain contact stops, 0–1
    /// @param restitution   how much of a body's speed into the terrain it bounces back with, 0–1
    /// @param selfCollision whether the bodies push each other apart, apart from the ones a joint links
    /// @param sleepSpeed    the speed under which every body must stay for a second before the simulation rests
    public record RagdollParameters(int substeps, float gravityScale, float friction, float restitution, boolean selfCollision, float sleepSpeed) {
        public static final RagdollParameters DEFAULT = new RagdollParameters(8, 1.0F, 0.6F, 0.1F, true, 0.05F);

        public static final Codec<RagdollParameters> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.intRange(1, 32).optionalFieldOf("substeps", DEFAULT.substeps).forGetter(RagdollParameters::substeps),
                    Codec.FLOAT.optionalFieldOf("gravity_scale", DEFAULT.gravityScale).forGetter(RagdollParameters::gravityScale),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("friction", DEFAULT.friction).forGetter(RagdollParameters::friction),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("restitution", DEFAULT.restitution).forGetter(RagdollParameters::restitution),
                    Codec.BOOL.optionalFieldOf("self_collision", DEFAULT.selfCollision).forGetter(RagdollParameters::selfCollision),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("sleep_speed", DEFAULT.sleepSpeed).forGetter(RagdollParameters::sleepSpeed)
                ).apply(instance, RagdollParameters::new)
        );
    }
}
