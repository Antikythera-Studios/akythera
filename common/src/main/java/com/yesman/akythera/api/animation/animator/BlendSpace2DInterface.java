package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.core.animation.driver.BlendSpace2DDriver;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import net.minecraft.core.Holder;

import java.util.function.Supplier;

public final class BlendSpace2DInterface extends AbstractAnimationInterface<BlendSpace2DDriver, BlendSpace2D> {
    private final Supplier<Float> xUpdater;
    private final Supplier<Float> yUpdater;

    public BlendSpace2DInterface(
        final Holder.Reference<BlendSpace2D> defaultAnimation,
        final Supplier<Float> xUpdater,
        final Supplier<Float> yUpdater
    ) {
        super(defaultAnimation);
        this.xUpdater = xUpdater;
        this.yUpdater = yUpdater;
    }

    @Override
    protected BlendSpace2DDriver instantiate(Holder.Reference<BlendSpace2D> animation) {
        return BlendSpace2D.instantiate(animation, xUpdater, yUpdater);
    }
}
