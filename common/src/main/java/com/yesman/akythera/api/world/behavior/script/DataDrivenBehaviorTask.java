package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTaskBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;

/// A task written in a data pack, run through the [BehaviorTaskScript] events of its [BehaviorTaskBlueprint]. The
/// task runs until a graph calls [#finishTask].
@ScriptType
public final class DataDrivenBehaviorTask extends DataDrivenBehaviorNode<BehaviorTaskBlueprint> implements BehaviorTask {
    /// What the task answers now: [TaskStatus#RUNNING] until a graph calls [#finishTask].
    private TaskStatus status = TaskStatus.RUNNING;

    public DataDrivenBehaviorTask(final Holder.Reference<BehaviorTaskBlueprint> blueprint, final BrainComponent brain) {
        super(blueprint, brain, BehaviorTaskBlueprint::body, BehaviorTaskScript.class);
    }

    @ScriptFunction(label = "Finish Task", category = "Behavior", desc = """
        Ends the task with success or failure
    """)
    public void finishTask(final boolean succeeded) {
        status = succeeded ? TaskStatus.SUCCEEDED : TaskStatus.FAILED;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        status = TaskStatus.RUNNING;
        getBlueprintExecutor().invokeMethod("onExecute", null, brain, this);

        return status;
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        getBlueprintExecutor().invokeMethod("onTick", null, brain, this);

        return status;
    }

    @Override
    public void onCanceled(final BrainComponent brain) {
        getBlueprintExecutor().invokeMethod("onCanceled", null, brain, this);
    }
}
