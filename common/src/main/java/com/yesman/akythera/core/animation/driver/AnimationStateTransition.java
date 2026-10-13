package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.math.model.Transition;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/// A transition type that is used in [AnimationStateMachine].
///
/// @param blendTime           An animation switching time span to the next state.
/// @param transferElapsedTime Whether the elapsed time of the previous state will be copied to the next state driver.
public record AnimationStateTransition<O extends ComponentAttachable>(
    FSMAnimationState nextState,
    BiFunction<AnimationStateMachine<O>.AnimationStateInstance, AnimationStateMachine<O>.AnimationStateInstance, Boolean> canTransitFunction,
    @Nullable BiConsumer<AnimationStateMachine<O>.AnimationStateInstance, AnimationStateMachine<O>.AnimationStateInstance> onTransitConsumer,
    int checkLatency,
    float blendTime,
    boolean transferElapsedTime,
    int transitionPriority
) implements Transition<AnimationStateMachine<O>.AnimationStateInstance, AnimationStateMachine<O>.AnimationStateInstance, FSMAnimationState> {
    @Override
    public boolean canTransit(AnimationStateMachine<O>.AnimationStateInstance from, AnimationStateMachine<O>.AnimationStateInstance to) {
        return canTransitFunction.apply(from, to);
    }

    @Override
    public void onTransit(AnimationStateMachine<O>.AnimationStateInstance from, AnimationStateMachine<O>.AnimationStateInstance to) {
        if (from.getMachine() != to.getMachine()) {
            throw new IllegalStateException("States are belonged to different state machines!");
        }

        from.getMachine().transferDriver(to, blendTime, transferElapsedTime);

        if (onTransitConsumer != null) onTransitConsumer.accept(from, to);
    }
}
