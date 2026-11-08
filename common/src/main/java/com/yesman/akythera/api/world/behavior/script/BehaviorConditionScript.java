package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.api.data.modularize.ScriptFunction;

/// The function a data pack condition graph overrides.
public interface BehaviorConditionScript {
    /// Whether the placement the condition is attached to may run. True while the graph does not override it.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean test(final BrainComponent brain, final DataDrivenBehaviorCondition condition) {
        return true;
    }
}
