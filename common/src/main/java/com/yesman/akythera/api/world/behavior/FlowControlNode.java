package com.yesman.akythera.api.world.behavior;

/// An inner node of a behavior tree: decides which children of its [TreeNode] run, through
/// [BehaviorTreeRunner#start], [BehaviorTreeRunner#update] and [BehaviorTreeRunner#cancel].
public interface FlowControlNode extends BehaviorNode {
    /// Starts the node at `placement`.
    TaskStatus start(final BehaviorTreeRunner tree, final TreeNode placement);

    /// Advances the node while it is [TaskStatus#RUNNING].
    TaskStatus update(final BehaviorTreeRunner tree, final TreeNode placement);

    /// Stops the node and its running children.
    void cancel(final BehaviorTreeRunner tree, final TreeNode placement);
}
