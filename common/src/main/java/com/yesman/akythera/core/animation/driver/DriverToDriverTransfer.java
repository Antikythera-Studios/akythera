package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// Transit from a [AnimationDriver] to another [AnimationDriver]
///
/// This driver isn't exposed to script reflection system.
public final class DriverToDriverTransfer extends DriverTransfer {
    private final AnimationDriver from;

    public DriverToDriverTransfer(
        final AnimationDriver from, final AnimationDriver to, final float maxBlendTime,
        final Runnable onFinished
    ) {
        super(to, maxBlendTime, onFinished);

        this.from = from;
    }

    @Override
    public Pose getFromPose() {
        return from.getPose();
    }

    @Override
    public void update(final float frameTime) {
        from.update(frameTime);

        super.update(frameTime);
    }
}
