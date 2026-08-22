package com.yesman.akythera.core.math.model;

import com.yesman.akythera.core.util.helper.ParseUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A component that represents the state of FSM
///
/// Each state instance can be paired with a [FSMState] by returning
/// [StateInstance#toSignature], then [FiniteStateMachine] will register
/// the instance with signature to avoid registering duplicated states.
public abstract class StateInstance<S extends FSMState, T extends Transition<?, ?, S>> {
    /// A list of transitions that this instance is connected in a state machine
    private final List<T> transitions;

    /// *delayed time* of state transitions
    private final Map<T, MutableInt> delayedTransitions = new HashMap<> ();

    public StateInstance(final List<T> transitions) {
        this.transitions = Collections.unmodifiableList(transitions);
    }

    /// Initializes the state
    /// this method is called after [Transition#onTransit] is called from a previous state
    protected void onStart() {
    }

    /// Clears the state
    /// this method is called before [Transition#onTransit] is called to a next state
    protected void onStop() {
    }

    /// Updates the state
    /// @return if the current state is interruptable and transit to next state
    public final boolean update() {
        // Increment the delay time and remove expired ones
        delayedTransitions.entrySet().removeIf(entry -> entry.getValue().incrementAndGet() >= entry.getKey().checkLatency());
        return tick();
    }

    /// Update the instance.
    ///
    /// Note: check won't occur in the current tick
    ///
    /// @return whether the state can transition to next states. If `false`, transition
    protected abstract boolean tick();

    /// @param transition a transition
    ///
    /// @return whether the transition is delayed or not
    public boolean isDelayed(final T transition) {
        return delayedTransitions.containsKey(transition);
    }

    /// Sets the transition delayed
    public void setDelayed(final T transition) {
        delayedTransitions.put(transition, new MutableInt());
    }

    /// Returns enum signature
    public abstract S toSignature();

    /// Return a debug message for this state
    public String getSignature() {
        return ParseUtils.toLowerCase(toSignature().toString());
    }

    /// Next states that this state can translate, See with [Transition]
    public List<T> transitions() {
        return transitions;
    }
}