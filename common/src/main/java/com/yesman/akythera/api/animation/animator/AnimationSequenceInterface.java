package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationPlayer;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

public final class AnimationSequenceInterface extends AbstractAnimationInterface<AnimationDriver, AnimationSequence> {
    public AnimationSequenceInterface(Holder.@NonNull Reference<AnimationSequence> defaultAnimation) {
        super(defaultAnimation);
    }

    @Override
    protected AnimationDriver instantiate(Holder.Reference<AnimationSequence> animation) {
        return AnimationPlayer.createInstance(animation);
    }
}
