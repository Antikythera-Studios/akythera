package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.api.data.modularize.ScriptFunction;

/// The events a data pack subtask graph implements.
public interface BehaviorSubtaskScript {
    /// Called when the placement the subtask is attached to starts running.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onBecomeActive(final BrainComponent brain, final DataDrivenBehaviorSubtask subtask);

    /// Called once per interval while the placement runs.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onTick(final BrainComponent brain, final DataDrivenBehaviorSubtask subtask);

    /// Called when the placement stops running.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onBecomeInactive(final BrainComponent brain, final DataDrivenBehaviorSubtask subtask);
}
