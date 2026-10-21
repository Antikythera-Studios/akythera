package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public abstract class AbstractWrapperDriver implements AnimationDriver {
    protected final AnimationDriver base;

    protected AbstractWrapperDriver(final AnimationDriver base) {
        this.base = base;
    }

    @Override
    public Pose getPose() {
        return base.getPose();
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return base.getPlayingAnimation();
    }

    @Override
    public void update(final float frameTime) {
        base.update(frameTime);
    }

    @Override
    public void tick(final ComponentAttachable owner) {
        base.tick(owner);
    }

    @Override
    public float getTime() {
        return base.getTime();
    }

    @Override
    public float getPhase() {
        return base.getPhase();
    }

    @Override
    public void start() {
        base.start();
    }

    @Override
    public void terminate() {
        base.terminate();
    }

    @Override
    public float getWeight() {
        return base.getWeight();
    }

    @Override
    public boolean isFinished() {
        return base.isFinished();
    }

    @Override
    public boolean fadingInOrOut() {
        return base.fadingInOrOut();
    }

    @Override
    public float getFadeTime() {
        return base.getFadeTime();
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        base.syncDriverTo(source);
    }
}
