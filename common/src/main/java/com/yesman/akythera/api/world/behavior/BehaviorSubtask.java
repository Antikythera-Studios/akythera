package com.yesman.akythera.api.world.behavior;

/// Work attached to a [TreeNode] that runs beside it while the placement is [TaskStatus#RUNNING].
public interface BehaviorSubtask extends BehaviorNode {
    /// Called when the placement starts running.
    default void onBecomeActive(final BrainComponent brain) {
    }

    /// Called on full brain ticks, once per [TreeNode.Subtask#getInterval].
    default void onTick(final BrainComponent brain) {
    }

    /// Called when the placement stops running.
    default void onBecomeInactive(final BrainComponent brain) {
    }
}
