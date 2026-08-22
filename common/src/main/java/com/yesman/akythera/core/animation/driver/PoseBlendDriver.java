package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.util.helper.PoseOperators;

import java.util.function.Supplier;

/// Interpolates between two poses by a live alpha: 0.0 is [AbstractBlendDriver#base] alone, 1.0 is
/// [AbstractBlendDriver#blend] alone.
public final class PoseBlendDriver extends AbstractBlendDriver {
    private final Supplier<Float> alphaValueProvider;

    /// @param alphaValueProvider Wire alpha value provider, in [0, 1]
    public PoseBlendDriver(final AnimationDriver base, final AnimationDriver blend, final Supplier<Float> alphaValueProvider) {
        super(base, blend);

        this.alphaValueProvider = alphaValueProvider;
    }

    @Override
    public Pose getPose() {
        float alpha = alpha();
        return PoseOperators.blendPoses(base.getPose(), 1.0F - alpha, blend.getPose(), alpha);
    }

    @Override
    protected AnimationDriver dominant() {
        return alpha() < 0.5F ? base : blend;
    }

    private float alpha() {
        return Math.clamp(alphaValueProvider.get(), 0.0F, 1.0F);
    }
}
