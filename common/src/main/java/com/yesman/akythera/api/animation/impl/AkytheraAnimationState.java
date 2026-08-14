package com.yesman.akythera.api.animation.impl;

import com.yesman.akythera.core.animation.driver.FSMAnimationState;

public enum AkytheraAnimationState implements FSMAnimationState {
    // A common state sign to represent a machine is ready to tart. Don't implement custom entry enum.
    ENTRY,
    // A common state sign to represent a machine is terminated. Don't implement custom exit enum.
    EXIT,
    ;

    final int id;

    AkytheraAnimationState() {
        this.id = FSMAnimationState.ENUM_MANAGER.assign(this);
    }

    @Override
    public int universalOrdinal() {
        return this.id;
    }
}
