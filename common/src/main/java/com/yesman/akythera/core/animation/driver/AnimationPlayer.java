package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
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

    /// Last time a frame ran the notifies to. Memorized for [PlayableAnimation#frameUpdate].
    private float lastFrameTime;

    // true if fading in, false if fading out
    private boolean fadingInOrOut;

    /// Whether [#terminate] ran since the last [#start]. A terminated play fades out without driving notifies.
    private boolean terminated;

    /// The owner of the last [#tick], which a stopped play's notify states are ended for.
    private @Nullable ComponentAttachable owner;

    /// Custom control of animation looping, has higher priority of [PlayableAnimation#loops]
    private final @Nullable Boolean loops;

    private final Holder<? extends @NonNull PlayableAnimation> playableAnimation;

    /// Use the static constructor [#createInstance]
    private AnimationPlayer(Holder<? extends @NonNull PlayableAnimation> playableAnimation, @Nullable Boolean loops) {
        this.playableAnimation = playableAnimation;
        this.loops = loops;
    }

    /// Restart the player from 0 time. Restarting a play that still runs interrupts it.
    @Override
    public void start() {
        interruptNotifies();
        fadingInOrOut = true;
        isFinished = false;
        terminated = false;
        fadeTime = 0.0F;
        elapsedTime = 0.0F;
        lastUpdatedTime = 0.0F;
        lastFrameTime = 0.0F;
    }

    @Override
    public void terminate() {
        interruptNotifies();
        fadingInOrOut = false;
        terminated = true;
    }

    /// Drives the underlying [PlayableAnimation]'s notify track for this tick, until the player is terminated.
    /// Mirrors [AnimationMontagePlayer#tick] so sequences carry notify support
    /// when this player runs them.
    public void tick(final ComponentAttachable owner) {
        this.owner = owner;

        if (!terminated && getWeight() >= 0.01F) playableAnimation.value().tick(owner, lastUpdatedTime, elapsedTime);
        lastUpdatedTime = elapsedTime;
    }

    /// Ends the notify states the playback sits inside, for a play that stops before its end.
    private void interruptNotifies() {
        if (owner != null && !terminated) playableAnimation.value().interrupt(owner, lastUpdatedTime, lastFrameTime);
    }

    /// Runs the notifies of the time the last frame moved to, whose pose is the one drawn now, then moves the time.
    @Override
    public void update(float frameTime) {
        // The owner is known from the first tick on. Until then, the frames' notifies wait
        if (owner != null) {
            if (!terminated && getWeight() >= 0.01F) playableAnimation.value().frameUpdate(owner, lastFrameTime, elapsedTime);
            lastFrameTime = elapsedTime;
        }

        if (fadingInOrOut) {
            fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, playableAnimation.value().fadeInTime());
        } else {
            fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, playableAnimation.value().fadeOutTime());
        }

        elapsedTime += frameTime * playableAnimation.value().rateScale();
        float totalFrameTime = playableAnimation.value().getTotalFrameTime();

        if (elapsedTime > totalFrameTime) {
            // Clamp to the max frame time when not looping, or apply modular operation
            // to restart animation from exceeded time
            if (loops()) {
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
    public float getPhase() {
        PlayableAnimation playable = playableAnimation.value();
        float phase = MathUtils.safeDivision(elapsedTime, playable.getTotalFrameTime(), 0.0F) - playable.phaseOffset();

        return loops() ? Mth.frac(phase) : Math.clamp(phase, 0.0F, 1.0F);
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
        PlayableAnimation playable = playableAnimation.value();
        float phase = source.getPhase() + playable.phaseOffset();

        fadeTime = source.getFadeTime();
        elapsedTime = (loops() ? Mth.frac(phase) : Math.min(phase, 1.0F)) * playable.getTotalFrameTime();
        lastUpdatedTime = elapsedTime;
        lastFrameTime = elapsedTime;
        isFinished = source.isFinished();
    }

    private boolean loops() {
        return ParseUtils.nvl(loops, playableAnimation.value().loops());
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