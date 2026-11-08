package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BehaviorCondition;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorConditionBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;

/// A condition written in a data pack, run through the [BehaviorConditionScript#test] override of its
/// [BehaviorConditionBlueprint]. A graph that throws, or answers anything but true, fails the condition.
@ScriptType
public final class DataDrivenBehaviorCondition extends DataDrivenBehaviorNode<BehaviorConditionBlueprint> implements BehaviorCondition {
    public DataDrivenBehaviorCondition(final Holder.Reference<BehaviorConditionBlueprint> blueprint, final BrainComponent brain) {
        super(blueprint, brain, BehaviorConditionBlueprint::body, BehaviorConditionScript.class);
    }

    @Override
    public boolean test(final BrainComponent brain) {
        return Boolean.TRUE.equals(getBlueprintExecutor().invokeMethod("test", () -> true, brain, this));
    }
}
