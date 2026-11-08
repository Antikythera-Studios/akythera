package com.yesman.akythera.api.world.behavior.statemachine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.behavior.MobBehaviorState;
import com.yesman.akythera.api.world.behavior.TreeNodeBlueprint;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.function.Consumer;

/// A state machine a [StateMachineTask] runs, stored in its behavior tree asset by name.
///
/// @param entryTransitions the transitions leaving the entry state
public record BehaviorStateMachineData(List<Transition> entryTransitions, List<State> states) {
    public static final Codec<BehaviorStateMachineData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Transition.CODEC.listOf().optionalFieldOf("entry_transitions", List.of()).forGetter(BehaviorStateMachineData::entryTransitions),
                State.CODEC.listOf().optionalFieldOf("states", List.of()).forGetter(BehaviorStateMachineData::states)
            ).apply(instance, BehaviorStateMachineData::new)
    );

    public BehaviorStateMachineData {
        entryTransitions = List.copyOf(entryTransitions);
        states = List.copyOf(states);
    }

    /// Visits the roots of each state's tree.
    public void forEachTree(final Consumer<TreeNodeBlueprint> visitor) {
        states.forEach(state -> state.roots().forEach(visitor));
    }

    /// A state: its signature, the behavior tree it runs, and the transitions leaving it.
    ///
    /// @param roots the placements the ROOT of the state's tree runs, left to right by priority. A state with none runs
    ///              nothing until a transition leaves it
    public record State(MobBehaviorState state, List<TreeNodeBlueprint> roots, List<Transition> transitions) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    MobBehaviorState.ENUM_MANAGER.getNameCodec().fieldOf("state").forGetter(State::state),
                    TreeNodeBlueprint.CODEC.listOf().optionalFieldOf("roots", List.of()).forGetter(State::roots),
                    Transition.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(State::transitions)
                ).apply(instance, State::new)
        );

        public State {
            roots = List.copyOf(roots);
            transitions = List.copyOf(transitions);
        }
    }

    /// A transition to `to` gated by `condition`.
    ///
    /// @param abortTaskImmediately whether the transition may stop a running task of the state's tree. Off, it is
    ///                             tested only while no task runs, or right after one ended
    /// @param checkLatency         server ticks a failed test waits before the next one
    /// @param transitionPriority   higher priorities are tested first
    /// @param canvas               the editor canvas `condition` was compiled from
    public record Transition(
        MobBehaviorState to, PureNode condition, boolean abortTaskImmediately, int checkLatency, int transitionPriority, String canvas
    ) {
        public static final Codec<Transition> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    MobBehaviorState.ENUM_MANAGER.getNameCodec().fieldOf("to").forGetter(Transition::to),
                    PureNode.CODEC.fieldOf("condition").forGetter(Transition::condition),
                    Codec.BOOL.optionalFieldOf("abort_task_immediately", false).forGetter(Transition::abortTaskImmediately),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("check_latency", 0).forGetter(Transition::checkLatency),
                    Codec.INT.optionalFieldOf("transition_priority", 0).forGetter(Transition::transitionPriority),
                    Codec.STRING.optionalFieldOf("canvas", ScriptNode.NO_CANVAS).forGetter(Transition::canvas)
                ).apply(instance, Transition::new)
        );
    }
}
