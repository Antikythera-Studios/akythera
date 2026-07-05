package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/// An "Injection Point" where you can dynamically
/// reload animation entries from [BlendingAnimator]
///
/// Practical uses would be a weapon specific animations
/// like holding greatsword, spear, which you can't replace
/// just by default locomotion.
public final class AnimationInterface implements PlayableAnimation {
    private final PlayableAnimation defaultAnimation;

    @NonNull
    private PlayableAnimation currentAnimation;

    public AnimationInterface(
        BlendingAnimator owner,
        AnimationInterfaceKey interfaceKey,
        @NonNull PlayableAnimation defaultAnimation
    ) {
        this.defaultAnimation = defaultAnimation;
        this.currentAnimation = defaultAnimation;

        // Register anim interface to replace entry at anytime
        owner.addAnimInterface(interfaceKey, this);
    }

    /// Get the current animation
    public PlayableAnimation get() {
        return currentAnimation;
    }

    /// Overwrite animation. Roll back to
    public void overwrite(PlayableAnimation playableAnimation) {
        currentAnimation = playableAnimation;
    }

    /// Roll back to default animation
    public void reset() {
        currentAnimation = defaultAnimation;
    }

    @Override
    public Pose getPose(float time) {
        return get().getPose(time);
    }

    @Override
    public float fadeInTime() {
        return get().fadeInTime();
    }

    @Override
    public float fadeOutTime() {
        return get().fadeOutTime();
    }

    @Override
    public float getTotalFrameTime() {
        return get().getTotalFrameTime();
    }

    @Override
    public boolean loops() {
        return get().loops();
    }

    @Override
    public void tick(final EntityPatch<Entity> owner, final float prevElapsedTime, final float currentElapsedTime) {
        get().tick(owner, prevElapsedTime, currentElapsedTime);
    }
}
