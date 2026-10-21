package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class CachedPoseReference implements AnimationDriver {
    // according pose cache must exist in animator
    private final String cacheName;

    private final BlendingAnimator blendingAnimator;

    private final boolean controlReferenceDriver;

    private float recordedAdvanceTime;

    public CachedPoseReference(final String cacheName, final BlendingAnimator blendingAnimator) {
        this(cacheName, blendingAnimator, false);
    }

    public CachedPoseReference(
        final String cacheName, final BlendingAnimator blendingAnimator, final boolean controlReferenceDriver
    ) {
        this.cacheName = cacheName;
        this.blendingAnimator = blendingAnimator;
        this.controlReferenceDriver = controlReferenceDriver;
    }

    @Override
    public Pose getPose() {
        Pose pose = blendingAnimator.getCachedPose(cacheName);

        // pose == null; the pose hasn't evaluated in this frame yet
        if (pose == null) {
            // Important: update the referencing drive only if the cached pose is empty.
            // This prevents double-stepping in a frame.
            ref().update(recordedAdvanceTime);
            pose = ref().getPose();
            blendingAnimator.cachePose(cacheName, pose);
        }

        return pose;
    }

    @Override
    public void tick(ComponentAttachable owner) {
        ref().tick(owner);
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return ref().getPlayingAnimation();
    }

    @Override
    public void update(final float frameTime) {
        recordedAdvanceTime = frameTime;
        // In this stage, we only record the time advances and update the referencing driver when pose got referenced.
    }

    @Override
    public float getTime() {
        return ref().getTime();
    }

    @Override
    public float getPhase() {
        return ref().getPhase();
    }

    @Override
    public void start() {
        if (controlReferenceDriver) ref().start();
    }

    @Override
    public void terminate() {
        if (controlReferenceDriver) ref().terminate();
    }

    @Override
    public float getWeight() {
        return ref().getWeight();//playbackControl ? ref().getWeight() : 0.0F;
    }

    @Override
    public boolean isFinished() {
        return ref().isFinished();
    }

    @Override
    public boolean fadingInOrOut() {
        return ref().fadingInOrOut();
    }

    @Override
    public float getFadeTime() {
        return ref().getFadeTime();
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        if (controlReferenceDriver) {
            ref().syncDriverTo(source);
        }
    }

    private AnimationDriver ref() {
        return blendingAnimator.getCachedPoseDriver(cacheName());
    }

    public String cacheName() {
        return cacheName;
    }
}
