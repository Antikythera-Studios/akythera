package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// An "Injection Point" where you can dynamically
/// reload animation entries from [BlendingAnimator]
///
/// Practical uses would be a weapon specific animations
/// like holding greatsword, spear, which you can't replace
/// just by default locomotion.
///
/// What an interface accepts is decided by [#instantiate] on the incoming animation, so it may play
/// an animation of a different kind than its default.
public abstract class AbstractAnimationInterface implements AnimationDriver {
    /// Default animation of the interface. Switches back on [#reset] is called
    private final Holder<? extends @NonNull PlayableAnimation> defaultAnimation;

    /// An animation driver that was played before a transition occurs. Lifecycle
    /// sustains until the weight becomes 0.0 and becomes `null` after then.
    private @Nullable AnimationDriver animDriverO;

    /// Current animation driver. Null until [#replace] is called since constructed.
    private AnimationDriver animDriver;

    public AbstractAnimationInterface(
        final Holder<? extends @NonNull PlayableAnimation> defaultAnimation
    ) {
        this.defaultAnimation = defaultAnimation;
    }

    /// The driver that plays `animation` on this interface, or null when this interface cannot drive
    /// its type.
    protected abstract @Nullable AnimationDriver instantiate(final Holder<? extends @NonNull PlayableAnimation> animation);

    /// Replace the animation into `animationToReplace` with transition time.
    ///
    /// An animation this interface cannot drive leaves the current one playing.
    public final void replace(final Holder<? extends @NonNull PlayableAnimation> animationToReplace) {
        AnimationDriver newDriver = instantiate(animationToReplace);

        if (newDriver == null) {
            Akythera.LOGGER.error("Can't play {} on animation interface {}. The types are mismatches.",
                animationToReplace, getClass().getSimpleName());
            return;
        }

        if (animDriver != null) {
            animDriverO = animDriver;
            animDriverO.terminate();
        }

        animDriver = newDriver;
        animDriver.start();
    }

    /// Roll back to default animation
    ///
    /// Must be called after the interface is constructed to ensure
    /// all fields that are required for instantiating driver.
    ///
    /// @see AnimatorScript#instantiateBlendSpace2DInterface lazy init to ensure `xUpdater` and `yUpdater`
    public final void reset() {
        replace(defaultAnimation);
    }

    @Override
    public final Pose getPose() {
        float weight = animDriver.getWeight();
        boolean shouldBlend = animDriverO != null;

        return shouldBlend
            ? PoseOperators.blendPoses(animDriverO.getPose(), 1.0F - weight, animDriver.getPose(), weight)
            : animDriver.getPose();
    }

    @Override
    public final @Nullable PlayableAnimation getPlayingAnimation() {
        return animDriver.getPlayingAnimation();
    }

    @Override
    public final void update(final float frameTime) {
        // Update the old driver until it reaches 0
        if (animDriverO != null) {
            animDriverO.update(frameTime);
            animDriverO = animDriverO.getWeight() > 0.0F ? animDriverO : null;
        }

        animDriver.update(frameTime);
    }

    /// Drives the notify tracks of whatever this interface is playing, the outgoing animation included
    /// while it still has weight.
    @Override
    public final void tick(final ComponentAttachable owner) {
        if (animDriverO != null) {
            animDriverO.tick(owner);
        }

        animDriver.tick(owner);
    }

    @Override
    public final float getTime() {
        return animDriver.getTime();
    }

    @Override
    public final float getPhase() {
        return animDriver.getPhase();
    }

    @Override
    public final void start() {
        animDriver.start();
    }

    @Override
    public final void terminate() {
        animDriver.terminate();
    }

    @Override
    public final float getWeight() {
        // Internal blending mode. Outside driver regards the driver's blending weight as 1.0 when the interface is being replaced.
        if (animDriverO != null && animDriver.fadingInOrOut()) {
            return 1.0F;
        }

        return animDriver.getWeight();
    }

    @Override
    public final boolean isFinished() {
        return animDriver.isFinished();
    }

    @Override
    public final boolean fadingInOrOut() {
        return animDriver.fadingInOrOut();
    }

    @Override
    public float getFadeTime() {
        return animDriver.getFadeTime();
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        animDriver.syncDriverTo(source);
    }
}
