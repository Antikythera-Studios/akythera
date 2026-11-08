package com.yesman.akythera.core.asset.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.ik.IKChain;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import net.minecraft.world.phys.Vec3;

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
/// @param constraints bone name to the constraints of that bone, in the order they run; a bone with none is absent
/// @param simulation  the rag doll's solver settings
/// @param ikChains    the IK chains, each keyed by its tip bone
/// @param rootFitting how the chains' shared parent moves to fit the feet before the chains are solved
/// @param bodyTurn    how the shown body follows the body yaw while the chains' IK shows
public record ArmaturePhysicsData(
    Map<String, BoneBody> bodies, Map<String, List<BoneConstraint>> constraints, RagdollParameters simulation, List<IKChain> ikChains,
    RootFitting rootFitting, BodyTurn bodyTurn
) {
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
                Codec.unboundedMap(Codec.STRING, BoneConstraint.CODEC.listOf()).optionalFieldOf("constraints", Map.of())
                    .forGetter(ArmaturePhysicsData::constraints),
                RagdollParameters.CODEC.optionalFieldOf("simulation", RagdollParameters.DEFAULT)
                    .forGetter(ArmaturePhysicsData::simulation),
                IKChain.CODEC.listOf().optionalFieldOf("ik_chains", List.of())
                    .forGetter(ArmaturePhysicsData::ikChains),
                RootFitting.CODEC.optionalFieldOf("root_fitting", RootFitting.DEFAULT)
                    .forGetter(ArmaturePhysicsData::rootFitting),
                BodyTurn.CODEC.optionalFieldOf("body_turn", BodyTurn.DEFAULT)
                    .forGetter(ArmaturePhysicsData::bodyTurn)
            ).apply(instance, ArmaturePhysicsData::new)
    );

    /// One bone's rigid body.
    ///
    /// @param colliders      shapes in the bone's space, each pivoted by its own transform, each of [#BODY_SHAPES]
    /// @param mass           the body's mass, spread over its colliders by their volume
    /// @param linearDamping  velocity lost per second, as a fraction
    /// @param angularDamping angular velocity lost per second, as a fraction
    /// @param centerOfMassOffset where the center of mass sits from the colliders' own, in the bone's space, in blocks
    /// @param buoyancyScale      multiplier on the fluid the colliders push aside, for a body whose colliders are larger
    ///                           or smaller than what floats it
    public record BoneBody(
        List<Collider> colliders, float mass, float linearDamping, float angularDamping, Vec3 centerOfMassOffset, float buoyancyScale
    ) {
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
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("angular_damping", DEFAULT_ANGULAR_DAMPING).forGetter(BoneBody::angularDamping),
                    Vec3.CODEC.optionalFieldOf("center_of_mass_offset", Vec3.ZERO).forGetter(BoneBody::centerOfMassOffset),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("buoyancy_scale", 1.0F).forGetter(BoneBody::buoyancyScale)
                ).apply(instance, BoneBody::new)
        );
    }

    /// The solver settings of a [RagdollSimulation]. Lengths are in blocks, times in seconds.
    ///
    /// @param substeps      solver substeps per game tick, 1–32
    /// @param gravityScale  multiplier on 9.81 blocks/s² downward
    /// @param friction      how much of a body's sliding a terrain contact stops, 0–1
    /// @param restitution   how much of a body's speed into the terrain it bounces back with, 0–1
    /// @param selfCollision whether the bodies push each other apart, apart from the ones a joint links
    /// @param sleepSpeed    the speed under which every body must stay for a second before the simulation rests
    /// @param water         how water holds the bodies up and slows them
    /// @param lava          how lava holds the bodies up and slows them
    public record RagdollParameters(
        int substeps, float gravityScale, float friction, float restitution, boolean selfCollision, float sleepSpeed,
        FluidParameters water, FluidParameters lava
    ) {
        public static final RagdollParameters DEFAULT = new RagdollParameters(8, 1.0F, 0.6F, 0.1F, true, 0.05F, FluidParameters.WATER, FluidParameters.LAVA);

        public static final Codec<RagdollParameters> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.intRange(1, 32).optionalFieldOf("substeps", DEFAULT.substeps).forGetter(RagdollParameters::substeps),
                    Codec.FLOAT.optionalFieldOf("gravity_scale", DEFAULT.gravityScale).forGetter(RagdollParameters::gravityScale),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("friction", DEFAULT.friction).forGetter(RagdollParameters::friction),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("restitution", DEFAULT.restitution).forGetter(RagdollParameters::restitution),
                    Codec.BOOL.optionalFieldOf("self_collision", DEFAULT.selfCollision).forGetter(RagdollParameters::selfCollision),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("sleep_speed", DEFAULT.sleepSpeed).forGetter(RagdollParameters::sleepSpeed),
                    FluidParameters.CODEC.optionalFieldOf("water", DEFAULT.water).forGetter(RagdollParameters::water),
                    FluidParameters.CODEC.optionalFieldOf("lava", DEFAULT.lava).forGetter(RagdollParameters::lava)
                ).apply(instance, RagdollParameters::new)
        );
    }

    /// How the IK chains' shared parent moves to fit the feet: lowered so the lowest foot reaches its ground, then
    /// tilted parallel to the ground under the feet. Two chains tilt it along the line through their feet only; one
    /// chain only lowers it. Both fade with the chains' IK weight.
    ///
    /// @param maxDrop  farthest the parent lowers, in blocks
    /// @param maxPitch farthest the parent tilts around the armature's X axis, in degrees
    /// @param maxRoll  farthest the parent tilts around the armature's Z axis, in degrees
    public record RootFitting(boolean enabled, float maxDrop, float maxPitch, float maxRoll) {
        public static final RootFitting DEFAULT = new RootFitting(false, 0.5F, 15.0F, 15.0F);

        public static final Codec<RootFitting> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(RootFitting::enabled),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("max_drop", DEFAULT.maxDrop).forGetter(RootFitting::maxDrop),
                    Codec.floatRange(0.0F, 90.0F).optionalFieldOf("max_pitch", DEFAULT.maxPitch).forGetter(RootFitting::maxPitch),
                    Codec.floatRange(0.0F, 90.0F).optionalFieldOf("max_roll", DEFAULT.maxRoll).forGetter(RootFitting::maxRoll)
                ).apply(instance, RootFitting::new)
        );
    }

    /// How the shown body eases after the body yaw at a limited speed. Fades with the chains' IK weight.
    ///
    /// @param turnTime         how long the shown body takes to catch up with a turn, in seconds
    /// @param maxTurnSpeed     fastest the shown body turns while the animation places the chains' tips, in degrees per
    ///                         second
    /// @param maxStepTurnSpeed fastest the shown body turns while procedural stepping places the tips, in degrees per
    ///                         second
    /// @param steadyBones      bones that keep their facing while the shown body is behind the yaw, like a head looking
    ///                         at something
    public record BodyTurn(boolean enabled, float turnTime, float maxTurnSpeed, float maxStepTurnSpeed, List<String> steadyBones) {
        public static final BodyTurn DEFAULT = new BodyTurn(false, 0.2F, 360.0F, 180.0F, List.of());

        public static final Codec<BodyTurn> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(BodyTurn::enabled),
                    Codec.floatRange(0.01F, Float.MAX_VALUE).optionalFieldOf("turn_time", DEFAULT.turnTime).forGetter(BodyTurn::turnTime),
                    Codec.floatRange(1.0F, Float.MAX_VALUE).optionalFieldOf("max_turn_speed", DEFAULT.maxTurnSpeed).forGetter(BodyTurn::maxTurnSpeed),
                    Codec.floatRange(1.0F, Float.MAX_VALUE).optionalFieldOf("max_step_turn_speed", DEFAULT.maxStepTurnSpeed).forGetter(BodyTurn::maxStepTurnSpeed),
                    Codec.STRING.listOf().optionalFieldOf("steady_bones", DEFAULT.steadyBones).forGetter(BodyTurn::steadyBones)
                ).apply(instance, BodyTurn::new)
        );
    }

    /// How a fluid holds a rag doll's bodies up and slows them.
    ///
    /// @param density mass per cubic block, in the unit of [BoneBody#mass]: a body whose mass per volume is under it floats
    /// @param drag            velocity a fully submerged body loses per second, as a fraction
    /// @param dragCoefficient how hard the fluid resists a body moving through it, a force growing with the speed
    ///                        squared: 0.5 * [#density] * this * cross-section * speed^2
    public record FluidParameters(float density, float drag, float dragCoefficient) {
        public static final FluidParameters WATER = new FluidParameters(1000.0F, 2.0F, 1.0F);
        public static final FluidParameters LAVA = new FluidParameters(3000.0F, 6.0F, 1.0F);

        public static final Codec<FluidParameters> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("density").forGetter(FluidParameters::density),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("drag").forGetter(FluidParameters::drag),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("drag_coefficient", 1.0F).forGetter(FluidParameters::dragCoefficient)
                ).apply(instance, FluidParameters::new)
        );
    }
}
