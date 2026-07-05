package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class CachedPoseReference implements AnimationDriver {
    // according pose cache must exist in animator
    private final String cacheName;

    private final BlendingAnimator blendingAnimator;

    private float recordedAdvanceTime;

    public CachedPoseReference(final String cacheName, final BlendingAnimator blendingAnimator) {
        this.cacheName = cacheName;
        this.blendingAnimator = blendingAnimator;
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
    public void tick(EntityPatch<Entity> owner) {
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
    public void start() {
        ref().start();
    }

    @Override
    public void terminate() {
        ref().terminate();
    }

    @Override
    public float getWeight() {
        return ref().getWeight();
    }

    @Override
    public boolean isFinished() {
        return ref().isFinished();
    }

    private AnimationDriver ref() {
        return blendingAnimator.getCachedPoseDriver(cacheName());
    }

    public String cacheName() {
        return cacheName;
    }

    public BlendingAnimator blendingAnimator() {
        return blendingAnimator;
    }
}
