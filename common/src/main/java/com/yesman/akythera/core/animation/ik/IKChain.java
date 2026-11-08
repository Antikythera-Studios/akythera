package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// A run of bones that IK turns so the tail of its tip bone reaches a target, keyed by the tip bone.
///
/// @param tipBone     the chain's last bone, whose tail is placed
/// @param chainLength how many bones the chain spans, from the tip bone upward
/// @param solver      how the bones turn to reach the target
/// @param stepping    how procedural stepping moves the tip
public record IKChain(String tipBone, int chainLength, IKSolver solver, Stepping stepping) {
    public static final Codec<IKChain> CODEC = RecordCodecBuilder.<IKChain>create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("tip_bone").forGetter(IKChain::tipBone),
                Codec.intRange(1, 64).fieldOf("chain_length").forGetter(IKChain::chainLength),
                IKSolver.CODEC.fieldOf("solver").forGetter(IKChain::solver),
                Stepping.CODEC.optionalFieldOf("stepping", Stepping.DEFAULT).forGetter(IKChain::stepping)
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
    /// @param prediction        how far past the step's landing time the body's motion and turn are extrapolated to
    ///                          place the landing spot
    /// @param resetTime         how long after the last step the tip may step back to where the animation puts it
    /// @param resetDistance     how far the planted tip may stand from where the animation puts it without stepping back
    /// @param resetAngle        how far the body may turn away from the planted tip's facing without stepping back
    /// @param resetStepTime     how long a step back to where the animation puts the tip takes
    /// @param turnLead          the turn on which the tip's gait group steps first
    /// @param gaitGroup         the chains whose tips step together, one group at a time; empty steps alone
    /// @param holdFacing        whether a planted tip keeps its facing while the body turns, up to [#angularThreshold]
    public record Stepping(
        float distanceThreshold, float angularThreshold, float stepHeight, float rayMaxDistance, float stepTime, float stepCooldown,
        float prediction, float resetTime, float resetDistance, float resetAngle, float resetStepTime, TurnLead turnLead, String gaitGroup,
        boolean holdFacing
    ) {
        public static final Stepping DEFAULT = new Stepping(
            0.3F, 45.0F, 0.15F, 1.0F, 0.25F, 0.1F, 0.1F, 0.5F, 0.1F, 15.0F, 0.25F, TurnLead.NONE, "", false
        );

        public static final Codec<Stepping> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("distance_threshold", DEFAULT.distanceThreshold).forGetter(Stepping::distanceThreshold),
                    Codec.floatRange(0.0F, 180.0F).optionalFieldOf("angular_threshold", DEFAULT.angularThreshold).forGetter(Stepping::angularThreshold),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("step_height", DEFAULT.stepHeight).forGetter(Stepping::stepHeight),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("ray_max_distance", DEFAULT.rayMaxDistance).forGetter(Stepping::rayMaxDistance),
                    Codec.floatRange(0.01F, Float.MAX_VALUE).optionalFieldOf("step_time", DEFAULT.stepTime).forGetter(Stepping::stepTime),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("step_cooldown", DEFAULT.stepCooldown).forGetter(Stepping::stepCooldown),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("prediction", DEFAULT.prediction).forGetter(Stepping::prediction),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("reset_time", DEFAULT.resetTime).forGetter(Stepping::resetTime),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("reset_distance", DEFAULT.resetDistance).forGetter(Stepping::resetDistance),
                    Codec.floatRange(0.0F, 180.0F).optionalFieldOf("reset_angle", DEFAULT.resetAngle).forGetter(Stepping::resetAngle),
                    Codec.floatRange(0.01F, Float.MAX_VALUE).optionalFieldOf("reset_step_time", DEFAULT.resetStepTime).forGetter(Stepping::resetStepTime),
                    TurnLead.CODEC.optionalFieldOf("turn_lead", DEFAULT.turnLead).forGetter(Stepping::turnLead),
                    Codec.STRING.optionalFieldOf("gait_group", DEFAULT.gaitGroup).forGetter(Stepping::gaitGroup),
                    Codec.BOOL.optionalFieldOf("hold_facing", DEFAULT.holdFacing).forGetter(Stepping::holdFacing)
                ).apply(instance, Stepping::new)
        );
    }

    /// The turn on which a tip's gait group steps first. A right turn raises the body yaw.
    public enum TurnLead implements StringRepresentable {
        NONE("none"),
        LEFT("left"),
        RIGHT("right");

        public static final Codec<TurnLead> CODEC = StringRepresentable.fromEnum(TurnLead::values);

        private final String name;

        TurnLead(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
