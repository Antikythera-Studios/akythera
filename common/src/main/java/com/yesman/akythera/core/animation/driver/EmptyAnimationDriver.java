package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Stub driver for special purposes
public final class EmptyAnimationDriver implements AnimationDriver {
    /// Shared stub. The driver holds no state, so one instance serves every user of it.
    public static final EmptyAnimationDriver INSTANCE = new EmptyAnimationDriver();

    private EmptyAnimationDriver() {}

    @Override
    public Pose getPose() {
        return Pose.EMPTY_POSE;
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return null;
    }

    @Override
    public void update(float frameTime) {
    }

    @Override
    public float getTime() {
        return 0.0F;
    }

    @Override
    public void start() {
    }

    @Override
    public void terminate() {
    }

    @Override
    public float getWeight() {
        return 1.0F;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean fadingInOrOut() {
        return true;
    }

    @Override
    public float getFadeTime() {
        return 0;
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
    }
}
