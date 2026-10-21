package com.yesman.akythera.core.animation.driver;

import java.util.function.Supplier;

public final class PlayRateDriver extends AbstractWrapperDriver {
    private final Supplier<Float> rateUpdater;

    public PlayRateDriver(final AnimationDriver base, final Supplier<Float> rateUpdater) {
        super(base);

        this.rateUpdater = rateUpdater;
    }

    @Override
    public void update(final float frameTime) {
        base.update(frameTime * Math.max(rateUpdater.get(), 0.0F));
    }
}
