package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.world.behavior.TaskStatus;

/// Runs children left to right until one fails.
public final class SequenceNode extends OrderedFlowNode {
    public SequenceNode() {
        super(TaskStatus.FAILED, false);
    }
}
