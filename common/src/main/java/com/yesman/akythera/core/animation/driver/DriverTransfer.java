package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Smoothly switch from a pose to another [AnimationDriver]
///
/// These types of drivers aren't exposed to script reflection system.
public abstract class DriverTransfer implements AnimationDriver {
    private final AnimationDriver to;
    private final float maxBlendTime;
    private final Runnable onFinished;

    private boolean finished;
    private float alpha;

    public DriverTransfer(
        final AnimationDriver to, final float maxBlendTime, final Runnable onFinished
    ) {
        this.to = to;
        this.maxBlendTime = maxBlendTime;
        this.onFinished = onFinished;
    }

    protected abstract Pose getFromPose();

    @Override
    public Pose getPose() {
        float blending = alpha / maxBlendTime;
        return PoseOperators.blendPoses(getFromPose(), 1.0F - blending, to.getPose(), blending);
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return to.getPlayingAnimation();
    }

    @Override
    public void update(final float frameTime) {
        to.update(frameTime);

        if (finished) return;

        alpha += frameTime;

        if (alpha >= maxBlendTime) {
            finished = true;
            alpha = maxBlendTime;
            onFinished.run();
        }
    }

    @Override
    public float getTime() {
        return alpha;
    }

    @Override
    public void start() {
        finished = false;
        alpha = 0.0F;
    }

    @Override
    public void terminate() {
        finished = true;
    }

    @Override
    public float getWeight() {
        return finished ? 0.0F : 1.0F;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    // Temp drivers are not meant to be synced to any other drivers
    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
    }

    // Temp drivers are not meant to be faded
    @Override
    public float getFadeTime() {
        return 0.0F;
    }

    /// [PoseToDriverTransfer] doesn't have a concept for "fade in, out"
    @Override @Deprecated
    public boolean fadingInOrOut() {
        throw new UnsupportedOperationException("PoseTransfer doesn't support fade in and fade out.");
    }
}
