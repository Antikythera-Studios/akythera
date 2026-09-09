package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationPlayer;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class AnimationSequenceInterface extends AbstractAnimationInterface {
    public AnimationSequenceInterface(Holder<@NonNull AnimationSequence> defaultAnimation) {
        super(defaultAnimation);
    }

    /// A sequence rides [AnimationPlayer]. This interface carries no axis value suppliers, so a
    /// [BlendSpace2D] is refused.
    @Override
    protected @Nullable AnimationDriver instantiate(final Holder<? extends @NonNull PlayableAnimation> animation) {
        return animation.value() instanceof AnimationSequence ? AnimationPlayer.createInstance(animation) : null;
    }
}
