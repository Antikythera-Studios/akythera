package com.yesman.akythera.core.math.model;

/// A transition that is occurred when meeting specific condition,
/// starts from @param <From> state, and translates to @param <To> state
///
/// @param <From> a state before the transition
/// @param <To>   a state after the transition
public interface Transition<From extends StateInstance<S, ?>, To extends StateInstance<S, ?>, S extends FSMState> {
    /// Returns a state signature of next state
    S nextState();

    /// Returns a condition of this transition to occur
    boolean canTransit(final From from, final To to);

    /// Called on transition is occurred, after [#canTransit] returns true
    void onTransit(final From from, final To to);

    /// Returns ticks to delay until the next [#canTransit] check
    int checkLatency();

    /// Returns a priority of transition estimation. Higher numbers are checked first.
    int transitionPriority();
}
