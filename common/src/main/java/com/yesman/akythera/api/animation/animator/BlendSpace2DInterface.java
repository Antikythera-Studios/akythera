package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationPlayer;
import com.yesman.akythera.core.animation.driver.BlendSpace2DDriver;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class BlendSpace2DInterface extends AbstractAnimationInterface {
    private final Supplier<Float> xUpdater;
    private final Supplier<Float> yUpdater;

    public BlendSpace2DInterface(
        final Holder<@NonNull BlendSpace2D> defaultAnimation,
        final Supplier<Float> xUpdater,
        final Supplier<Float> yUpdater
    ) {
        super(defaultAnimation);
        this.xUpdater = xUpdater;
        this.yUpdater = yUpdater;
    }

    /// A blend space rides [BlendSpace2DDriver] on this interface's axis suppliers; a sequence rides
    /// [AnimationPlayer].
    @Override
    @SuppressWarnings("unchecked")
    protected @Nullable AnimationDriver instantiate(final Holder<? extends @NonNull PlayableAnimation> animation) {
        return switch (animation.value()) {
            case BlendSpace2D ignored -> BlendSpace2D.instantiate((Holder<BlendSpace2D>) animation, xUpdater, yUpdater);
            case AnimationSequence ignored -> AnimationPlayer.createInstance(animation);
            default -> null;
        };
    }
}
