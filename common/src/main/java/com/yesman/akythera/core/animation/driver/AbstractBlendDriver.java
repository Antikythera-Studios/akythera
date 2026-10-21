package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// A driver whose pose comes from mixing two child drivers. Both children run on this driver's clock
/// — they are updated, ticked, started and terminated together — while the playback state this driver
/// reports (playing animation, time, finish and fade flags) is read from [#dominant], the child
/// currently carrying the mix.
///
/// Subclasses supply the mixing rule in [AnimationDriver#getPose].
public abstract class AbstractBlendDriver implements AnimationDriver {
    /// The pose the mix starts from.
    protected final AnimationDriver base;

    /// The pose mixed onto [#base].
    protected final AnimationDriver blend;

    protected AbstractBlendDriver(final AnimationDriver base, final AnimationDriver blend) {
        this.base = base;
        this.blend = blend;
    }

    /// The child this driver reports its playback state from.
    protected abstract AnimationDriver dominant();

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return dominant().getPlayingAnimation();
    }

    @Override
    public void update(final float frameTime) {
        base.update(frameTime);
        blend.update(frameTime);
    }

    @Override
    public void tick(final ComponentAttachable owner) {
        base.tick(owner);
        blend.tick(owner);
    }

    @Override
    public float getTime() {
        return dominant().getTime();
    }

    @Override
    public float getPhase() {
        return dominant().getPhase();
    }

    @Override
    public void start() {
        base.start();
        blend.start();
    }

    @Override
    public void terminate() {
        base.terminate();
        blend.terminate();
    }

    /// Fades with whichever child is furthest into its own fade, so a blend used as a state's driver
    /// ramps in and out like a single player.
    @Override
    public float getWeight() {
        return Math.max(base.getWeight(), blend.getWeight());
    }

    @Override
    public boolean isFinished() {
        return dominant().isFinished();
    }

    @Override
    public boolean fadingInOrOut() {
        return dominant().fadingInOrOut();
    }

    @Override
    public float getFadeTime() {
        return dominant().getFadeTime();
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        base.syncDriverTo(source);
        blend.syncDriverTo(source);
    }
}
