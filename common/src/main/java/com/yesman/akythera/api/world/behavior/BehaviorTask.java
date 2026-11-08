package com.yesman.akythera.api.world.behavior;

/// A leaf of a behavior tree: the action a brain takes.
public interface BehaviorTask extends BehaviorNode {
    /// Starts the task.
    TaskStatus doTask(final BrainComponent brain);

    /// Advances the task while it is [TaskStatus#RUNNING], on full brain ticks, and on the others too when
    /// [#requiresUpdateEveryTick] is true.
    TaskStatus tickTask(final BrainComponent brain);

    /// Called when the task stops while [TaskStatus#RUNNING].
    default void onCanceled(final BrainComponent brain) {
    }
}
