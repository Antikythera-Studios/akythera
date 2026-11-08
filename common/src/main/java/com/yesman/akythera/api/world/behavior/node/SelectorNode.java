package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.world.behavior.TaskStatus;

/// Runs children left to right until one succeeds.
public final class SelectorNode extends OrderedFlowNode {
    public SelectorNode() {
        super(TaskStatus.SUCCEEDED, true);
    }
}
