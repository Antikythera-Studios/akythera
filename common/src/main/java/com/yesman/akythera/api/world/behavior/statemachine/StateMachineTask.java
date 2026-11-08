package com.yesman.akythera.api.world.behavior.statemachine;

import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.core.Akythera;
import org.jspecify.annotations.Nullable;

/// Runs the state machine [#stateMachine] of the brain's behavior tree asset. Has no end of its own: it succeeds only
/// once a transition reaches the exit state, and fails when the asset holds no such state machine or one of its
/// state trees can't be built.
public final class StateMachineTask implements BehaviorTask {
    /// The name the asset stores the state machine under.
    private final String stateMachine;

    /// The machine the last start built, or null before one.
    private @Nullable BehaviorStateMachine machine;

    public StateMachineTask(final String stateMachine) {
        this.stateMachine = stateMachine;
    }

    public String getStateMachine() {
        return stateMachine;
    }

    public @Nullable BehaviorStateMachine getMachine() {
        return machine;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        BehaviorTreeInstance treeInstance = brain.getTreeInstance();
        BehaviorStateMachineData data = treeInstance == null ? null : treeInstance.getBlueprint().value().stateMachines().get(stateMachine);

        if (data == null) return TaskStatus.FAILED;

        try {
            machine = new BehaviorStateMachine(brain, treeInstance, data);
        } catch (IllegalStateException e) {
            Akythera.LOGGER.warn("Can't build state machine {} for {}", stateMachine, brain.getOwner().getOriginal(), e);
            machine = null;

            return TaskStatus.FAILED;
        }

        return tickTask(brain);
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        if (machine == null) return TaskStatus.FAILED;

        machine.tick(brain.isFullTick());

        return machine.terminated() ? TaskStatus.SUCCEEDED : TaskStatus.RUNNING;
    }

    @Override
    public void onCanceled(final BrainComponent brain) {
        if (machine != null) machine.stop();
    }
}
