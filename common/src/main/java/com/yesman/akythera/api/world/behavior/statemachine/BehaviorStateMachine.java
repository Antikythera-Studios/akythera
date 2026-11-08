package com.yesman.akythera.api.world.behavior.statemachine;

import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.api.world.behavior.AkytheraMobBehaviorState;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BehaviorTreeRunner;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.MobBehaviorState;
import com.yesman.akythera.api.world.behavior.TreeNodeBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.model.FiniteStateMachine;
import com.yesman.akythera.core.math.model.StateInstance;
import com.yesman.akythera.core.math.model.StateModel;
import com.yesman.akythera.core.math.model.Transition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// A [BehaviorStateMachineData] built for one brain. Each state runs its own tree, built for the brain with the
/// machine, and a transition that does not abort tasks immediately waits until no task of the state's tree runs.
public final class BehaviorStateMachine implements FiniteStateMachine<
    BrainComponent, MobBehaviorState, BehaviorStateMachine.State, BehaviorStateMachine.StateTransition
> {
    /// The local a transition condition reads the brain from.
    public static final String BRAIN_LOCAL = "brain";

    private final BrainComponent brain;

    /// The asset the transition conditions run as.
    private final BehaviorTreeInstance treeInstance;

    private final StateModel<MobBehaviorState, State, StateTransition, BehaviorStateMachine> stateModel;

    private MobBehaviorState state = AkytheraMobBehaviorState.ENTRY;

    /// Whether the brain tick in progress is a full one.
    private boolean fullTick;

    /// @throws IllegalStateException when a data pack node of a state's tree names a missing entry
    public BehaviorStateMachine(final BrainComponent brain, final BehaviorTreeInstance treeInstance, final BehaviorStateMachineData data) {
        this.brain = brain;
        this.treeInstance = treeInstance;

        StateModel.StateModelBuilder<BrainComponent, MobBehaviorState, State, StateTransition, BehaviorStateMachine> builder =
            StateModel.builder(stateBuilder(AkytheraMobBehaviorState.ENTRY, null), stateBuilder(AkytheraMobBehaviorState.EXIT, null));

        sortedByPriority(data.entryTransitions()).forEach(transition -> builder.transitTo(new StateTransition(transition)));

        for (BehaviorStateMachineData.State stateData : data.states()) {
            builder.addState(stateData.state(), stateBuilder(stateData.state(), stateData.roots()));
            sortedByPriority(stateData.transitions()).forEach(transition -> builder.transitTo(new StateTransition(transition)));
        }

        this.stateModel = builder.build(this);
    }

    /// Advances the current state's tree and tests the transitions leaving it.
    public void tick(final boolean fullTick) {
        this.fullTick = fullTick;
        tick();
    }

    /// Stops the current state's tree and goes back to the entry state.
    public void stop() {
        stateModel.getStateInstance(state).runner.stop();
        state = AkytheraMobBehaviorState.ENTRY;
    }

    /// Skips a transition that does not abort tasks immediately while a task of the current state's tree runs, unless
    /// one ended during this tick.
    @Override
    public boolean checkAndTransitToNextState() {
        State current = stateModel.getStateInstance(state);
        boolean betweenTasks = current.runner.consumeTaskEnded() || !current.runner.isTaskRunning();

        for (StateTransition transition : current.transitions()) {
            if (current.isDelayed(transition) || (!transition.data.abortTaskImmediately() && !betweenTasks)) continue;

            State next = stateModel.getStateInstance(transition.nextState());

            if (transition.canTransit(current, next)) {
                current.onStop();
                setState(transition.nextState());
                transition.onTransit(current, next);

                return true;
            }

            if (transition.checkLatency() > 0) current.setDelayed(transition);
        }

        return false;
    }

    /// The runner of the current state's tree.
    public BehaviorTreeRunner getCurrentRunner() {
        return stateModel.getStateInstance(state).runner;
    }

    @Override
    public MobBehaviorState getState() {
        return state;
    }

    @Override
    public void setState(final MobBehaviorState state) {
        this.state = state;
    }

    @Override
    public StateModel<MobBehaviorState, State, StateTransition, ?> getStateModel() {
        return stateModel;
    }

    @Override
    public BrainComponent getOwner() {
        return brain;
    }

    @Override
    public MobBehaviorState getEntryState() {
        return AkytheraMobBehaviorState.ENTRY;
    }

    @Override
    public MobBehaviorState getExitState() {
        return AkytheraMobBehaviorState.EXIT;
    }

    private StateModel.StateInstanceBuilder<MobBehaviorState, State, StateTransition, BehaviorStateMachine> stateBuilder(
        final MobBehaviorState signature, final List<TreeNodeBlueprint> roots
    ) {
        return new StateModel.StateInstanceBuilder<>() {
            @Override
            protected State makeInstance(final BehaviorStateMachine owner) {
                return new State(transitions, signature, new BehaviorTreeRunner(brain, roots.stream().map(root -> root.instantiate(brain)).toList()));
            }
        };
    }

    private static List<BehaviorStateMachineData.Transition> sortedByPriority(final List<BehaviorStateMachineData.Transition> transitions) {
        return transitions.stream().sorted(Comparator.comparingInt(BehaviorStateMachineData.Transition::transitionPriority).reversed()).toList();
    }

    /// A state running its tree on every brain tick.
    public final class State extends StateInstance<MobBehaviorState, StateTransition> {
        private final MobBehaviorState signature;

        private final BehaviorTreeRunner runner;

        private State(final List<StateTransition> transitions, final MobBehaviorState signature, final BehaviorTreeRunner runner) {
            super(transitions);

            this.signature = signature;
            this.runner = runner;
        }

        @Override
        protected boolean tick() {
            runner.tick(fullTick);
            return true;
        }

        @Override
        protected void onStop() {
            runner.stop();
        }

        @Override
        public MobBehaviorState toSignature() {
            return signature;
        }
    }

    /// A transition testing its condition against the asset's blackboard, with the brain bound to [#BRAIN_LOCAL].
    public final class StateTransition implements Transition<State, State, MobBehaviorState> {
        private final BehaviorStateMachineData.Transition data;

        private StateTransition(final BehaviorStateMachineData.Transition data) {
            this.data = data;
        }

        @Override
        public MobBehaviorState nextState() {
            return data.to();
        }

        /// An empty or non-boolean condition passes, a throwing one fails.
        @Override
        public boolean canTransit(final State from, final State to) {
            ScriptVariables variables = treeInstance.getScriptVariable();
            Map<String, Object> interrupted = variables.captureLocals();

            variables.setValue(BRAIN_LOCAL, brain);

            PureNode condition = data.condition();
            ScriptNode.ExecutionContext context = ScriptRecorder.liveContext(ScriptCaller.of(treeInstance, data.canvas()), variables);

            try {
                Object value = condition.execute(treeInstance, context);

                return !(value instanceof Boolean bool) || bool;
            } catch (Throwable throwable) {
                ScriptRecorder.recordFailure(context, throwable);

                return false;
            } finally {
                variables.restoreLocals(interrupted);
            }
        }

        @Override
        public void onTransit(final State from, final State to) {
        }

        @Override
        public int checkLatency() {
            return data.checkLatency();
        }

        @Override
        public int transitionPriority() {
            return data.transitionPriority();
        }
    }
}
