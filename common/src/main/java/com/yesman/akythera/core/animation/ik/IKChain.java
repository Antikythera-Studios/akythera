package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// A run of bones that IK turns so the tail of its tip bone reaches a target, keyed by the tip bone.
///
/// @param tipBone       the chain's last bone, whose tail is placed
/// @param chainLength   how many bones the chain spans, from the tip bone upward
/// @param solver        how the bones turn to reach the target
/// @param stepping      how procedural stepping moves the tip
/// @param alignToNormal whether the bones under the tip bone tilt to the slope of the ground the tip stands on
public record IKChain(String tipBone, int chainLength, IKSolver solver, Stepping stepping, boolean alignToNormal) {
    public static final Codec<IKChain> CODEC = RecordCodecBuilder.<IKChain>create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("tip_bone").forGetter(IKChain::tipBone),
                Codec.intRange(1, 64).fieldOf("chain_length").forGetter(IKChain::chainLength),
                IKSolver.CODEC.fieldOf("solver").forGetter(IKChain::solver),
                Stepping.CODEC.optionalFieldOf("stepping", Stepping.DEFAULT).forGetter(IKChain::stepping),
                Codec.BOOL.optionalFieldOf("align_to_normal", false).forGetter(IKChain::alignToNormal)
            ).apply(instance, IKChain::new)
    ).validate(chain -> chain.solver().supportsLength(chain.chainLength())
        ? DataResult.success(chain)
        : DataResult.error(() -> "IK chain " + chain.tipBone() + ": " + chain.solver().type().identifier() + " can't solve " + chain.chainLength() + " bones"));

    /// How procedural stepping moves a chain's tip. Lengths are in blocks, angles in degrees, times in seconds.
    ///
    /// @param distanceThreshold how far the planted tip may drift from where the animation puts it before it steps
    /// @param angularThreshold  how far the body may turn away from the planted tip's facing before it steps
    /// @param stepHeight        how high a step lifts the tip
    /// @param rayMaxDistance    how far below the animated tip the ground is searched for; with none found, the tip
    ///                          follows the animation
    /// @param stepTime          how long a step takes
    /// @param stepCooldown      how long after a step the next one waits; meanwhile a tip past a threshold slides
    public record Stepping(
        float distanceThreshold, float angularThreshold, float stepHeight, float rayMaxDistance, float stepTime, float stepCooldown
    ) {
        public static final Stepping DEFAULT = new Stepping(0.3F, 45.0F, 0.15F, 1.0F, 0.25F, 0.1F);

        public static final Codec<Stepping> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("distance_threshold", DEFAULT.distanceThreshold).forGetter(Stepping::distanceThreshold),
                    Codec.floatRange(0.0F, 180.0F).optionalFieldOf("angular_threshold", DEFAULT.angularThreshold).forGetter(Stepping::angularThreshold),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("step_height", DEFAULT.stepHeight).forGetter(Stepping::stepHeight),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("ray_max_distance", DEFAULT.rayMaxDistance).forGetter(Stepping::rayMaxDistance),
                    Codec.floatRange(0.01F, Float.MAX_VALUE).optionalFieldOf("step_time", DEFAULT.stepTime).forGetter(Stepping::stepTime),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("step_cooldown", DEFAULT.stepCooldown).forGetter(Stepping::stepCooldown)
                ).apply(instance, Stepping::new)
        );
    }
}
