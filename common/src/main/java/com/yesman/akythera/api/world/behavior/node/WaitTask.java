package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;

/// Succeeds after [#ticks].
public final class WaitTask implements BehaviorTask {
    /// How long the task runs, in server ticks.
    private final int ticks;

    /// Updates left until the task succeeds.
    private int ticksLeft;

    public WaitTask(final int ticks) {
        this.ticks = ticks;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        ticksLeft = adjustedTickDelay(ticks);

        return ticksLeft > 0 ? TaskStatus.RUNNING : TaskStatus.SUCCEEDED;
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        return --ticksLeft > 0 ? TaskStatus.RUNNING : TaskStatus.SUCCEEDED;
    }
}
