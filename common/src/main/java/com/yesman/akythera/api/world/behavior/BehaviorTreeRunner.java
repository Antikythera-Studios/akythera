package com.yesman.akythera.api.world.behavior;

import com.yesman.akythera.api.world.behavior.node.SelectorNode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Runs one tree of placements built for one brain. ROOT runs its placements left to right by priority, as a
/// [SelectorNode] runs its children.
///
/// [FlowControlNode]s run their children through [#start], [#update] and [#cancel].
public final class BehaviorTreeRunner {
    private final BrainComponent brain;

    /// The selector placement over the placements ROOT runs, or null for a tree with none, which does nothing.
    private final @Nullable TreeNode root;

    /// How many [BehaviorTask]s are [TaskStatus#RUNNING].
    private int runningTasks;

    /// Whether a [BehaviorTask] stopped since the last [#consumeTaskEnded].
    private boolean taskEnded;

    /// Whether the brain tick in progress is a full one.
    private boolean fullTick;

    public BehaviorTreeRunner(final BrainComponent brain, final List<TreeNode> roots) {
        this.brain = brain;
        this.root = roots.isEmpty() ? null : new TreeNode(new SelectorNode(), roots, List.of(), List.of(), AbortMode.NONE);
    }

    public BrainComponent getBrain() {
        return brain;
    }

    /// The placements ROOT runs, left to right.
    public List<TreeNode> getRoots() {
        return root == null ? List.of() : root.getChildren();
    }

    /// Advances the tree, starting it from the root on a full tick once the last run ended.
    public void tick(final boolean fullTick) {
        if (root == null || (!root.isRunning() && !fullTick)) return;

        this.fullTick = fullTick;

        if (root.isRunning()) {
            update(root);
        } else {
            start(root);
        }
    }

    /// Cancels the running placements.
    public void stop() {
        if (root != null && root.isRunning()) cancel(root);
    }

    /// Whether the brain tick in progress is a full one.
    public boolean isFullTick() {
        return fullTick;
    }

    /// Whether a [BehaviorTask] of this tree is [TaskStatus#RUNNING].
    public boolean isTaskRunning() {
        return runningTasks > 0;
    }

    /// Whether a [BehaviorTask] stopped since the last call, which clears it.
    public boolean consumeTaskEnded() {
        boolean ended = taskEnded;
        taskEnded = false;

        return ended;
    }

    /// Starts `placement` when its conditions pass.
    public TaskStatus start(final TreeNode placement) {
        if (!testConditions(placement, true)) return TaskStatus.FAILED;

        TaskStatus status = switch (placement.getNode()) {
            case BehaviorTask task -> task.doTask(brain);
            case FlowControlNode flowControlNode -> flowControlNode.start(this, placement);
            default -> TaskStatus.FAILED;
        };

        if (status == TaskStatus.RUNNING) {
            setRunning(placement, true);

            long gameTime = brain.getOwner().getLevel().getGameTime();

            for (TreeNode.Subtask subtask : placement.getSubtasks()) {
                subtask.setNextTick(gameTime);
                subtask.getSubtask().onBecomeActive(brain);
            }
        }

        return status;
    }

    /// Advances `placement` while it is [TaskStatus#RUNNING]. A failing condition stops it when its abort mode says
    /// so. A task that does not update every tick waits for the next full tick.
    public TaskStatus update(final TreeNode placement) {
        if (placement.getAbortMode().abortsSelf() && !testConditions(placement, fullTick)) {
            cancel(placement);
            return TaskStatus.FAILED;
        }

        if (fullTick) {
            long gameTime = brain.getOwner().getLevel().getGameTime();

            for (TreeNode.Subtask subtask : placement.getSubtasks()) {
                if (gameTime < subtask.getNextTick()) continue;

                subtask.setNextTick(gameTime + subtask.getInterval());
                subtask.getSubtask().onTick(brain);
            }
        }

        TaskStatus status = switch (placement.getNode()) {
            case BehaviorTask task -> fullTick || task.requiresUpdateEveryTick() ? task.tickTask(brain) : TaskStatus.RUNNING;
            case FlowControlNode flowControlNode -> flowControlNode.update(this, placement);
            default -> TaskStatus.FAILED;
        };

        if (status != TaskStatus.RUNNING) finish(placement);

        return status;
    }

    /// Stops `placement` while it is [TaskStatus#RUNNING].
    public void cancel(final TreeNode placement) {
        switch (placement.getNode()) {
            case BehaviorTask task -> task.onCanceled(brain);
            case FlowControlNode flowControlNode -> flowControlNode.cancel(this, placement);
            default -> {}
        }

        finish(placement);
    }

    /// Tests the conditions of `placement` again and answers whether they all pass now after one of them failed
    /// last time.
    public boolean conditionsTurnedTrue(final TreeNode placement) {
        boolean passed = placement.getConditions().stream().allMatch(TreeNode.Condition::getLastResult);

        return testConditions(placement, fullTick) && !passed;
    }

    /// Tests the conditions of `placement`, all of them or only those that update every tick, and answers whether
    /// every last result passes.
    private boolean testConditions(final TreeNode placement, final boolean all) {
        boolean passed = true;

        for (TreeNode.Condition condition : placement.getConditions()) {
            if (all || condition.getCondition().requiresUpdateEveryTick()) condition.setLastResult(condition.getCondition().test(brain));

            passed &= condition.getLastResult();
        }

        return passed;
    }

    /// Marks `placement` as stopped and deactivates its subtasks.
    private void finish(final TreeNode placement) {
        if (!placement.isRunning()) return;

        setRunning(placement, false);
        placement.getSubtasks().forEach(subtask -> subtask.getSubtask().onBecomeInactive(brain));
    }

    private void setRunning(final TreeNode placement, final boolean value) {
        placement.setRunning(value);

        if (!(placement.getNode() instanceof BehaviorTask)) return;

        runningTasks += value ? 1 : -1;
        taskEnded |= !value;
    }
}
