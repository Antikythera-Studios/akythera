package com.yesman.akythera.core.math.model;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A definition of states, transitions of a state machine
public class StateModel<S extends FSMState, I extends StateInstance<S>, M extends FiniteStateMachine<?, S, I>> {
    /// State instances by signatures (extensible enum)
    private final Map<S, I> stateMap;

    public StateModel(final M owner, final StateModelBuilder<?, S, I, M> stateModelBuilder) {
        stateMap = new HashMap<> ();

        I entryInstance = stateModelBuilder.entryStateBuilder.makeInstance(owner);

        if (!stateModelBuilder.exitStateProvider.transitions.isEmpty()) {
            stateModelBuilder.exitStateProvider.transitions.clear();
            Akythera.LOGGER.warn("{} Builder: Exit state contains some transitions which is redundant.", owner.getClass().getSimpleName());
        }

        I exitInstance = stateModelBuilder.exitStateProvider.makeInstance(owner);

        // Add entry and exit states
        stateMap.put(entryInstance.toSignature(), entryInstance);
        stateMap.put(exitInstance.toSignature(), exitInstance);

        stateModelBuilder.states.forEach((signature, stateBp) -> {
            I stateInstance = stateBp.makeInstance(owner);

            if (stateInstance.toSignature() != signature)
                throw new IllegalStateException("Signatures doesn't match! state instance: " + stateInstance.toSignature() + " but provided " + signature);

            stateMap.put(signature, stateInstance);
        });
    }

    /// Returns the matching state instance for a @param stateSignature
    public I getStateInstance(final S stateSignature) {
        if (!stateMap.containsKey(stateSignature))
            throw new IllegalArgumentException(stateSignature + " is not defined in this finite state machine model");

        return stateMap.get(stateSignature);
    }

    public static <T, S extends FSMState, I extends StateInstance<S>, M extends FiniteStateMachine<T, S, I>> StateModelBuilder<T, S, I, M> builder(
        final StateInstanceBuilder<S, I, M> entryStateProvider,
        final StateInstanceBuilder<S, I, M> exitStateProvider
    ) {
        return new StateModelBuilder<>(entryStateProvider, exitStateProvider);
    }

    /// Creates mathematical state model with multiple states and their transits under a specific condition.
    ///
    /// @param <S> A state sign by [EnumerableDataBlock]. Each state is identified by this signature.
    /// @param <I> An instance of state used by [FiniteStateMachine]. It is highly a non-static class to communicate with fields
    ///            in the state machine class. The inner classes for instances could require parameters over the transitions, so
    ///            each state machine defines custom class inherits [StateInstanceBuilder].
    /// @param <M> A state machine type that is called on build the state model. See [#build].
    public static class StateModelBuilder<T, S extends FSMState, I extends StateInstance<S>, M extends FiniteStateMachine<T, S, I>> {
        private final Map<S, StateInstanceBuilder<S, I, M>> states = new HashMap<> ();
        private final StateInstanceBuilder<S, I, M> entryStateBuilder;
        private final StateInstanceBuilder<S, I, M> exitStateProvider;

        private StateInstanceBuilder<S, I, M> currentState;

        private StateModelBuilder(
            final StateInstanceBuilder<S, I, M> entryStateProvider,
            final StateInstanceBuilder<S, I, M> exitStateProvider
        ) {
            this.exitStateProvider = exitStateProvider;
            this.entryStateBuilder = entryStateProvider;
            currentState = entryStateBuilder;
        }

        /// Starts to build a new state, calling any [#transitTo] will be attached to this state until this method is called again
        public StateModelBuilder<T, S, I, M> addState(
            final S stateSignature,
            final StateInstanceBuilder<S, I, M> stateInstanceProvider
        ) {
            currentState = stateInstanceProvider;
            states.put(stateSignature, currentState);
            return this;
        }

        /// Define a transition of the current state
        /// the priority of each transition test follows the definition order
        ///
        /// @param nextState 	A state to switch
        /// @param when 		A condition for this transition to occurs
        /// @param onTransit 	A task when this transition occurs. Null when nothing will happen.
        /// @param delayTime    A tick how long the transition check will occur again after failure.
        public <From extends I, To extends I> StateModelBuilder<T, S, I, M> transitTo(
            final S nextState,
            final Transition.@NonNull TransitionCheck<From, To> when,
            final Transition.@Nullable TransitionJob<From, To> onTransit,
            int delayTime
        ) {
            currentState.transitions.add(new Transition<> (nextState, when, onTransit, delayTime));
            return this;
        }

        /// Define a transition of the current state without transition job and delay time
        public <From extends I, To extends I> StateModelBuilder<T, S, I, M> transitTo(
            final S nextState,
            final Transition.TransitionCheck<From, To> when
        ) {
            return transitTo(nextState, when, null, 0);
        }

        /// Define a transition of the current state without a delay time
        public <From extends I, To extends I> StateModelBuilder<T, S, I, M> transitTo(
            final S nextState,
            final Transition.TransitionCheck<From, To> when,
            final Transition.TransitionJob<From, To> onTransit
        ) {
            return transitTo(nextState, when, onTransit, 0);
        }

        /// Define a transition of the current state without a transition job
        public <From extends I, To extends I> StateModelBuilder<T, S, I, M> transitTo(
            final S nextState,
            final Transition.TransitionCheck<From, To> when,
            final int delayTime
        ) {
            return transitTo(nextState, when, null, delayTime);
        }

        public StateModel<S, I, M> build(M owner) {
            return new StateModel<>(owner, this);
        }

        public M makeStateMachine(T owner, final StateMachineConstructor<T, S, I, M> constructor) {
            return constructor.construct(owner, this);
        }
    }

    @FunctionalInterface
    public interface StateMachineConstructor<T, S extends FSMState, I extends StateInstance<S>, M extends FiniteStateMachine<T, S, I>> {
        M construct(T owner, StateModelBuilder<T, S, I, M> builder);
    }

    /// A delegate supplier of [StateInstance] to accept parameters dynamically, since the class could be inherited
    /// by non-static inner classes.
    ///
    /// Extend this class with parameters that a state instance of a machine requires in the constructor, then call
    /// the instance creation method you'd declare in each state machine's class.
    public static abstract class StateInstanceBuilder<S extends FSMState, T extends StateInstance<S>, M extends FiniteStateMachine<?, S, T>> {
        protected final List<Transition<?, ?, ?>> transitions = new ArrayList<>();

        /// Creates the instance by the owner machine. Since non-static classes can't be instantiated outside their
        /// outer class's scope, declare instantiating methods in state machine class then call them with arguments
        protected abstract T makeInstance(M owner);
    }
}
