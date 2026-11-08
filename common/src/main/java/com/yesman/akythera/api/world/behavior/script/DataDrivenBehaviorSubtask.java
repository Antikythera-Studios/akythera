package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BehaviorSubtask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorSubtaskBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;

/// A subtask written in a data pack, run through the [BehaviorSubtaskScript] events of its [BehaviorSubtaskBlueprint].
@ScriptType
public final class DataDrivenBehaviorSubtask extends DataDrivenBehaviorNode<BehaviorSubtaskBlueprint> implements BehaviorSubtask {
    public DataDrivenBehaviorSubtask(final Holder.Reference<BehaviorSubtaskBlueprint> blueprint, final BrainComponent brain) {
        super(blueprint, brain, BehaviorSubtaskBlueprint::body, BehaviorSubtaskScript.class);
    }

    @Override
    public void onBecomeActive(final BrainComponent brain) {
        getBlueprintExecutor().invokeMethod("onBecomeActive", null, brain, this);
    }

    @Override
    public void onTick(final BrainComponent brain) {
        getBlueprintExecutor().invokeMethod("onTick", null, brain, this);
    }

    @Override
    public void onBecomeInactive(final BrainComponent brain) {
        getBlueprintExecutor().invokeMethod("onBecomeInactive", null, brain, this);
    }
}
