package com.yesman.akythera.api.animation.animator;

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
/// Practical uses would be a weapon specific animationsToReplace
/// like holding greatsword, spear, which you can't replace
/// just by default locomotion.
public abstract class AbstractAnimationInterface<AD extends AnimationDriver, PA extends PlayableAnimation> implements AnimationDriver {
    /// Default animation of the interface. Switches back on [#reset] is called
    private final Holder.@NonNull Reference<PA> defaultAnimation;

    /// An animation driver that was played before a transition occurs. Lifecycle
    /// sustains until the weight becomes 0.0 and becomes `null` after then.
    private @Nullable AD animDriverO;

    /// Current animation driver. Null until [#replace] is called since constructed.
    private AD animDriver;

    public AbstractAnimationInterface(
        final Holder.@NonNull Reference<PA> defaultAnimation
    ) {
        this.defaultAnimation = defaultAnimation;
    }

    /// Instantiate animation player
    protected abstract AD instantiate(final Holder.Reference<PA> animation);

    /// Replace the animation into `animationToReplace` with transition time.
    @SuppressWarnings("unchecked")
    public final void replace(final Holder.Reference<? extends PlayableAnimation> animationToReplace) {
        try {
            AD newDriver = instantiate((Holder.Reference<PA>)animationToReplace);

            if (animDriver != null) {
                animDriverO = animDriver;
                animDriverO.terminate();
            }

            animDriver = newDriver;
            animDriver.start();
        } catch (ClassCastException e) {
            Akythera.LOGGER.error("Can't play {} on animation interface {}. The types are mismatches.",
                animationToReplace.key(), getClass().getSimpleName());
        }
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

    @Override
    public final float getTime() {
        return animDriver.getTime();
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
