package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.api.data.modularize.ScriptFunction;

/// The events a data pack task graph implements. The task runs until a graph calls
/// [DataDrivenBehaviorTask#finishTask].
public interface BehaviorTaskScript {
    /// Called when the task starts.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onExecute(final BrainComponent brain, final DataDrivenBehaviorTask task);

    /// Called on each update while the task runs.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onTick(final BrainComponent brain, final DataDrivenBehaviorTask task);

    /// Called when the task stops before it finished.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void onCanceled(final BrainComponent brain, final DataDrivenBehaviorTask task);
}
