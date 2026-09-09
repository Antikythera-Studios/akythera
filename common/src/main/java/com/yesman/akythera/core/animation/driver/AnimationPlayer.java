package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// A single [PlayableAnimation] driver
///
/// The player won't fade out automatically when finished. Check [#isFinished] in animation
/// logics if you want to make the player start fading out. Or use [AnimationMontage] and
/// [AnimationMontagePlayer] since they automatically being faded out when playing animation
/// ends.
public final class AnimationPlayer implements AnimationDriver {
    private float elapsedTime;
    private float fadeTime;
    private boolean isFinished;

    /// Last time updated in fixed tick. Memorized for [PlayableAnimation#tick].
    private float lastUpdatedTime;

    // true if fading in, false if fading out
    private boolean fadingInOrOut;

    /// Custom control of animation looping, has higher priority of [PlayableAnimation#loops]
    private final @Nullable Boolean loops;

    private final Holder<? extends @NonNull PlayableAnimation> playableAnimation;

    /// Use the static constructor [#createInstance]
    private AnimationPlayer(Holder<? extends @NonNull PlayableAnimation> playableAnimation, @Nullable Boolean loops) {
        this.playableAnimation = playableAnimation;
        this.loops = loops;
    }

    /// Restart the player from 0 time
    @Override
    public void start() {
        fadingInOrOut = true;
        isFinished = false;
        fadeTime = 0.0F;
        elapsedTime = 0.0F;
        lastUpdatedTime = 0.0F;
    }

    @Override
    public void terminate() {
        fadingInOrOut = false;
    }

    /// Drives the underlying [PlayableAnimation]'s notify track for this tick.
    /// Mirrors [AnimationMontagePlayer#tick] so sequences carry notify support
    /// when this player runs them.
    public void tick(final EntityPatch<Entity> owner) {
        if (getWeight() >= 0.01F) playableAnimation.value().tick(owner, lastUpdatedTime, elapsedTime);
        lastUpdatedTime = elapsedTime;
    }

    @Override
    public void update(float frameTime) {
        if (fadingInOrOut) {
            fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, playableAnimation.value().fadeInTime());
        } else {
            fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, playableAnimation.value().fadeOutTime());
        }

        elapsedTime += frameTime;
        float totalFrameTime = playableAnimation.value().getTotalFrameTime();

        if (elapsedTime > totalFrameTime) {
            boolean loopback = ParseUtils.nvl(loops, playableAnimation.value().loops());

            // Clamp to the max frame time when not looping, or apply modular operation
            // to restart animation from exceeded time
            if (loopback) {
                elapsedTime %= totalFrameTime;
            } else {
                elapsedTime = totalFrameTime;
                isFinished = true;
            }
        }
    }

    @Override
    public float getTime() {
        return elapsedTime;
    }

    @Override
    public float getWeight() {
        if (fadingInOrOut) {
            return MathUtils.safeDivision(fadeTime, playableAnimation.value().fadeInTime(), 1.0F);
        } else {
            return MathUtils.safeDivision(fadeTime, playableAnimation.value().fadeOutTime(), 0.0F);
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean fadingInOrOut() {
        return fadingInOrOut;
    }

    @Override
    public float getFadeTime() {
        return fadeTime;
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        fadeTime = source.getFadeTime();
        elapsedTime = source.getTime();
        lastUpdatedTime = source.getTime();
        isFinished = source.isFinished();
    }

    @Override
    public Pose getPose() {
        return playableAnimation.value().getPose(elapsedTime);
    }

    @Override
    public PlayableAnimation getPlayingAnimation() {
        return playableAnimation.value();
    }

    @Override
    public String toString() {
        return playableAnimation + " " + elapsedTime;
    }

    public static AnimationPlayer createInstance(Holder<? extends @NonNull PlayableAnimation> holder) {
        return new AnimationPlayer(holder, null);
    }

    public static AnimationPlayer createInstance(Holder<? extends @NonNull PlayableAnimation> holder, boolean loops) {
        return new AnimationPlayer(holder, loops);
    }
}