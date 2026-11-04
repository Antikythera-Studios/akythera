package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.keyframe.FloatCurve;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// The curves an animation clip drives one IK chain with under [AkytheraIKType#ANIMATION_CURVE], keyed by the chain's
/// tip bone.
///
/// @param alpha mix of the animated tip (0) and the IK tip (1); absent reads as 1
/// @param plant 1 holds the foot where it stood when the curve reached 1, 0 lets it follow the animation; absent reads
///              as 0
public record IKCurves(@Nullable FloatCurve alpha, @Nullable FloatCurve plant) {
    public static final Codec<IKCurves> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                FloatCurve.CODEC.optionalFieldOf("ik_alpha").forGetter(ParseUtils.provideOptional(IKCurves::alpha)),
                FloatCurve.CODEC.optionalFieldOf("plant").forGetter(ParseUtils.provideOptional(IKCurves::plant))
            ).apply(instance, IKCurves::new)
    );

    // Serialization
    public IKCurves(final Optional<FloatCurve> alpha, final Optional<FloatCurve> plant) {
        this(alpha.orElse(null), plant.orElse(null));
    }

    /// Whether neither curve is set.
    public boolean isEmpty() {
        return alpha == null && plant == null;
    }

    /// The chain's channel at `time`, with the missing curves at their defaults.
    public IKChannel channelAt(final float time) {
        return new IKChannel(alpha == null ? 1.0F : alpha.valueAt(time), plant == null ? 0.0F : plant.valueAt(time), 0.0F);
    }
}
