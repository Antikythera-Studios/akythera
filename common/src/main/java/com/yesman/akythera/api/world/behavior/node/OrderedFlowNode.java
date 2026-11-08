package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.world.behavior.AbortMode;
import com.yesman.akythera.api.world.behavior.BehaviorTreeRunner;
import com.yesman.akythera.api.world.behavior.FlowControlNode;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.api.world.behavior.TreeNode;

/// Runs children left to right until one answers [#finishingStatus], which the node then answers. Answers the
/// opposite once every child ran.
public abstract class OrderedFlowNode implements FlowControlNode {
    /// The child result that ends the node.
    private final TaskStatus finishingStatus;

    /// Whether an earlier child whose conditions turn true takes over from the running one, see
    /// [AbortMode#LOWER_PRIORITY].
    private final boolean earlierChildrenAbort;

    /// The child that answered [TaskStatus#RUNNING] last.
    private int runningChild;

    protected OrderedFlowNode(final TaskStatus finishingStatus, final boolean earlierChildrenAbort) {
        this.finishingStatus = finishingStatus;
        this.earlierChildrenAbort = earlierChildrenAbort;
    }

    @Override
    public TaskStatus start(final BehaviorTreeRunner tree, final TreeNode placement) {
        return runFrom(tree, placement, 0);
    }

    @Override
    public TaskStatus update(final BehaviorTreeRunner tree, final TreeNode placement) {
        TreeNode running = placement.getChildren().get(runningChild);

        if (earlierChildrenAbort) {
            for (int i = 0; i < runningChild; i++) {
                TreeNode earlier = placement.getChildren().get(i);

                if (earlier.getAbortMode().abortsLowerPriority() && tree.conditionsTurnedTrue(earlier)) {
                    tree.cancel(running);
                    return runFrom(tree, placement, i);
                }
            }
        }

        TaskStatus status = tree.update(running);

        return status == TaskStatus.RUNNING || status == finishingStatus
            ? status
            : runFrom(tree, placement, runningChild + 1);
    }

    @Override
    public void cancel(final BehaviorTreeRunner tree, final TreeNode placement) {
        TreeNode running = placement.getChildren().get(runningChild);

        if (running.isRunning()) tree.cancel(running);
    }

    private TaskStatus runFrom(final BehaviorTreeRunner tree, final TreeNode placement, final int first) {
        for (int i = first; i < placement.getChildren().size(); i++) {
            TaskStatus status = tree.start(placement.getChildren().get(i));

            if (status == TaskStatus.RUNNING) {
                runningChild = i;
                return status;
            }

            if (status == finishingStatus) return status;
        }

        return finishingStatus == TaskStatus.SUCCEEDED ? TaskStatus.FAILED : TaskStatus.SUCCEEDED;
    }
}
