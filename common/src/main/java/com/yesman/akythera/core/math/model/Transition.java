package com.yesman.akythera.core.math.model;

import org.jetbrains.annotations.Nullable;

/// A transition that is occurred when meeting specific condition, starts from @param <From> state, and translates to @param <To> state
///
/// @param <From>           a state before this transition occurs
/// @param <To>             a state before this transition occurs
/// @param nextState        a next state signature
/// @param when             a condition of this transition to occur
/// @param onTransit        a task when the transition occurred
/// @param checkingDelay    a time of delay until the next check when checking is failed
public record Transition<From extends StateInstance<?>, To extends StateInstance<S>, S extends FSMState> (
    S nextState,
    TransitionCheck<From, To> when,
    @Nullable TransitionJob<From, To> onTransit,
    int checkingDelay
) {
    /// A task when transition occurs
    /// @param <From>   a state instance connected in front
    /// @param <To>     a state instance connected behind
    @FunctionalInterface
    public interface TransitionCheck<From extends StateInstance<?>, To extends StateInstance<?>> {
        boolean check(From from, To to);
    }

	/// A task when transition occurs
	/// @param <From>   a state instance connected in front
	/// @param <To>     a state instance connected behind
	@FunctionalInterface
	public interface TransitionJob<From extends StateInstance<?>, To extends StateInstance<?>> {
		void doJob(From from, To to);
	}
}
