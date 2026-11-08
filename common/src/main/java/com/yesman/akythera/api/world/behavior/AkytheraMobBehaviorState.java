package com.yesman.akythera.api.world.behavior;

public enum AkytheraMobBehaviorState implements MobBehaviorState {
    // A common state sign to represent a machine is ready to start. Don't implement custom entry enum.
    ENTRY,
    // A common state sign to represent a machine is terminated. Don't implement custom exit enum.
    EXIT,
    ;

    final int id;

    AkytheraMobBehaviorState() {
        this.id = MobBehaviorState.ENUM_MANAGER.assign(this);
    }

    @Override
    public int universalOrdinal() {
        return this.id;
    }
}
