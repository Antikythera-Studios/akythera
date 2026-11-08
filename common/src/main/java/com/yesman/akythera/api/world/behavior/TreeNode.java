package com.yesman.akythera.api.world.behavior;

import java.util.List;

/// One placement of a [BehaviorTask] or a [FlowControlNode] built for one brain, with the conditions and subtasks
/// attached to it, and what the [BehaviorTreeRunner] knows of each.
public final class TreeNode {
    private final BehaviorNode node;

    /// The placements a [FlowControlNode] runs, empty under a [BehaviorTask].
    private final List<TreeNode> children;

    /// Tested before the placement starts, and while it runs as [#abortMode] says.
    private final List<Condition> conditions;

    /// Run while the placement is [TaskStatus#RUNNING].
    private final List<Subtask> subtasks;

    private final AbortMode abortMode;

    /// Whether the placement is [TaskStatus#RUNNING].
    private boolean running;

    public TreeNode(
        final BehaviorNode node, final List<TreeNode> children, final List<Condition> conditions, final List<Subtask> subtasks,
        final AbortMode abortMode
    ) {
        this.node = node;
        this.children = List.copyOf(children);
        this.conditions = List.copyOf(conditions);
        this.subtasks = List.copyOf(subtasks);
        this.abortMode = abortMode;
    }

    public BehaviorNode getNode() {
        return node;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<Subtask> getSubtasks() {
        return subtasks;
    }

    public AbortMode getAbortMode() {
        return abortMode;
    }

    /// Whether the placement is [TaskStatus#RUNNING].
    public boolean isRunning() {
        return running;
    }

    void setRunning(final boolean running) {
        this.running = running;
    }

    /// A [BehaviorCondition] attached to a placement.
    public static final class Condition {
        private final BehaviorCondition condition;

        /// What the last test answered.
        private boolean lastResult;

        public Condition(final BehaviorCondition condition) {
            this.condition = condition;
        }

        public BehaviorCondition getCondition() {
            return condition;
        }

        public boolean getLastResult() {
            return lastResult;
        }

        void setLastResult(final boolean lastResult) {
            this.lastResult = lastResult;
        }
    }

    /// A [BehaviorSubtask] attached to a placement.
    public static final class Subtask {
        private final BehaviorSubtask subtask;

        /// Server ticks between two [BehaviorSubtask#onTick] calls.
        private final int interval;

        /// The game time of the next [BehaviorSubtask#onTick].
        private long nextTick;

        public Subtask(final BehaviorSubtask subtask, final int interval) {
            this.subtask = subtask;
            this.interval = interval;
        }

        public BehaviorSubtask getSubtask() {
            return subtask;
        }

        public int getInterval() {
            return interval;
        }

        long getNextTick() {
            return nextTick;
        }

        void setNextTick(final long nextTick) {
            this.nextTick = nextTick;
        }
    }
}
