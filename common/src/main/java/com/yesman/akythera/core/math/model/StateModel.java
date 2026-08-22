package com.yesman.akythera.core.math.model;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.EnumerableDataBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A definition of states, transitions of a state machine
public class StateModel<S extends FSMState, I extends StateInstance<S, T>, T extends Transition<I, I, S>, M extends FiniteStateMachine<?, S, I, T>> {
    /// State instances by signatures (extensible enum)
    private final Map<S, I> stateMap;

    public StateModel(final M owner, final StateModelBuilder<?, S, I, T, M> stateModelBuilder) {
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

    public static <O, S extends FSMState, I extends StateInstance<S, T>, T extends Transition<I, I, S>, M extends FiniteStateMachine<O, S, I, T>> StateModelBuilder<O, S, I, T, M> builder(
        final StateInstanceBuilder<S, I, T, M> entryStateProvider,
        final StateInstanceBuilder<S, I, T, M> exitStateProvider
    ) {
        return new StateModelBuilder<>(entryStateProvider, exitStateProvider);
    }

    /// Creates mathematical state model with multiple states and their transits under a specific condition.
    ///
    /// @param <O> An owner type of the state machine
    /// @param <S> A state sign by [EnumerableDataBlock]. Each state is identified by this signature.
    /// @param <I> An instance of state used by [FiniteStateMachine]. It is highly a non-static class to communicate with fields
    ///            in the state machine class. The inner classes for instances could require parameters over the transitions, so
    ///            each state machine defines custom class inherits [StateInstanceBuilder].
    /// @param <M> A state machine type that is called on build the state model. See [#build].
    public static class StateModelBuilder<O, S extends FSMState, I extends StateInstance<S, T>, T extends Transition<I, I, S>, M extends FiniteStateMachine<O, S, I, T>> {
        private final Map<S, StateInstanceBuilder<S, I, T, M>> states = new HashMap<> ();
        private final StateInstanceBuilder<S, I, T, M> entryStateBuilder;
        private final StateInstanceBuilder<S, I, T, M> exitStateProvider;

        private StateInstanceBuilder<S, I, T, M> currentState;

        private StateModelBuilder(
            final StateInstanceBuilder<S, I, T, M> entryStateProvider,
            final StateInstanceBuilder<S, I, T, M> exitStateProvider
        ) {
            this.exitStateProvider = exitStateProvider;
            this.entryStateBuilder = entryStateProvider;
            currentState = entryStateBuilder;
        }

        /// Starts to build a new state, calling any [#transitTo] will be attached to this state until this method is called again
        public StateModelBuilder<O, S, I, T, M> addState(
            final S stateSignature,
            final StateInstanceBuilder<S, I, T, M> stateInstanceProvider
        ) {
            currentState = stateInstanceProvider;
            states.put(stateSignature, currentState);

            return this;
        }

        /// Adds a transition of the current state
        public StateModelBuilder<O, S, I, T, M> transitTo(final T transition) {
            currentState.transitions.add(transition);
            return this;
        }

        public StateModel<S, I, T, M> build(final M owner) {
            return new StateModel<>(owner, this);
        }

        public M makeStateMachine(final O owner, final StateMachineConstructor<O, S, I, T, M> constructor) {
            return constructor.construct(owner, this);
        }
    }

    @FunctionalInterface
    public interface StateMachineConstructor<O, S extends FSMState, I extends StateInstance<S, T>, T extends Transition<I, I, S>, M extends FiniteStateMachine<O, S, I, T>> {
        M construct(final O owner, final StateModelBuilder<O, S, I, T, M> builder);
    }

    /// A delegate supplier of [StateInstance] to accept parameters dynamically, since the class could be inherited
    /// by non-static inner classes.
    ///
    /// Extend this class with parameters that a state instance of a machine requires in the constructor, then call
    /// the instance creation method you'd declare in each state machine's class.
    public static abstract class StateInstanceBuilder<S extends FSMState, I extends StateInstance<S, T>, T extends Transition<I, I, S>, M extends FiniteStateMachine<?, S, I, T>> {
        protected final List<T> transitions = new ArrayList<>();

        /// Creates the instance by the owner machine. Since non-static classes can't be instantiated outside their
        /// outer class's scope, declare instantiating methods in state machine class then call them with arguments
        protected abstract I makeInstance(final M owner);
    }
}
