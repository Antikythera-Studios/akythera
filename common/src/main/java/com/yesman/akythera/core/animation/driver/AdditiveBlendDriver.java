package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.util.helper.PoseOperators;

import java.util.function.Supplier;

/// Lays an additive pose over a base pose by a live alpha. [AbstractBlendDriver#blend] carries deltas
/// rather than a pose of its own, so the animation behind it must be imported as
/// [AnimationLoader.ImportType#ADDITIVE]; the base is what the mix reads as, whatever the alpha.
public final class AdditiveBlendDriver extends AbstractBlendDriver {
    private final Supplier<Float> alphaUpdater;

    /// @param alphaUpdater Wire alpha value provider, in [0, 1]
    public AdditiveBlendDriver(final AnimationDriver base, final AnimationDriver additive, final Supplier<Float> alphaUpdater) {
        super(base, additive);

        this.alphaUpdater = alphaUpdater;
    }

    @Override
    public Pose getPose() {
        return PoseOperators.additivePose(base.getPose(), blend.getPose(), Math.clamp(alphaUpdater.get(), 0.0F, 1.0F));
    }

    @Override
    protected AnimationDriver dominant() {
        return blend;
    }
}
