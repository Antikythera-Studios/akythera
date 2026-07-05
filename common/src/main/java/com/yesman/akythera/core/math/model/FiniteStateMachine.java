package com.yesman.akythera.core.math.model;

/// @param <T> owner type of the state machine
public interface FiniteStateMachine<T, S extends FSMState, I extends StateInstance<S>> {
    /// Returns a current "state", an extensible enum object.
    S getState();

    /// Sets the given state as the current state
    void setState(S state);

    /// Returns a state model, wildcard for last parameter since you're calling in the state machine itself.
    StateModel<S, I, ?> getStateModel();

    /// Returns an owner of this machine
    T getOwner();

    /// Default state process logic
    default void tick() {
        S currentState = getState();

        // update the current state
        boolean readyToSwitch = getStateModel().getStateInstance(currentState).update();

        // If the current state is ready to switch the next states, checks conditions of transitions
        if (readyToSwitch) {
            checkAndTransitToNextState();
        }
    }

    /// Check the current state and try to transit to the next state
    /// @return whether it transits to another state
    default boolean checkAndTransitToNextState() {
        S currentState = getState();
        StateInstance<S> stateInstance = getStateModel().getStateInstance(currentState);

        for (Transition<?, ?, ?> transition : stateInstance.transitions()) {
            // skip if the transition delayed
            if (stateInstance.isDelayed(transition)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Transition<StateInstance<S>, StateInstance<S>, S> casted = (Transition<StateInstance<S>, StateInstance<S>, S>)transition;
            S nextState = casted.nextState();
            StateInstance<S> nextInstance = getStateModel().getStateInstance(nextState);

            // test the transition condition
            if (casted.when().check(stateInstance, nextInstance)) {
                stateInstance.onStop();
                setState(nextState);

                // do the transition task, stateInstance is now the old state
                if (casted.onTransit() != null) casted.onTransit().doJob(stateInstance, nextInstance);
                nextInstance.onStart();

                // finish the loop
                return true;
            } else {
                // apply the check delay
                if (transition.checkingDelay() > 0) stateInstance.setDelayed(transition);
            }
        }

        return false;
    }

    /// Returns [FSMState] that represents an entry point of the state machine
    S getEntryState();

    /// Returns [FSMState] that represents an exit point of the state machine
    S getExitState();

    /// Checks whether the state machine is ready to start (meaning it's not running at the moment)
    default boolean readyToStart() {
        return getState() == getEntryState();
    }

    /// Cheks whether the state machine is terminated
    default boolean terminated() {
        return getState() == getExitState();
    }
}
