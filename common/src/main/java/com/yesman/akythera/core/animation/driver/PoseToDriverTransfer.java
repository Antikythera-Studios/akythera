package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;

/// Transit from a [Pose] to a playing [AnimationDriver]
///
/// This driver isn't exposed to script reflection system.
public final class PoseToDriverTransfer extends DriverTransfer {
    private final Pose from;

    public PoseToDriverTransfer(
        final Pose from, final AnimationDriver to, final float maxBlendTime, final Runnable onFinished
    ) {
        super(to, maxBlendTime, onFinished);
        this.from = from;
    }

    @Override
    public Pose getFromPose() {
        return from;
    }
}
