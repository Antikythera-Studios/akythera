package com.yesman.akythera.api.world.ability.impl;

import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTask;

/// A task that completes once the given ticks have passed.
public final class AwaitTask extends AbilityTask {
    /// Ticks left until the task completes.
    private int remainingTicks;

    public AwaitTask(final int ticks) {
        this.remainingTicks = ticks;
    }

    @Override
    public void onStart(final AbilitySystemComponent abilitySystemComponent) {
    }

    @Override
    public void tick(final AbilitySystemComponent abilitySystemComponent) {
        remainingTicks--;
    }

    @Override
    public boolean isEnd(final AbilitySystemComponent abilitySystemComponent) {
        return remainingTicks <= 0;
    }
}
