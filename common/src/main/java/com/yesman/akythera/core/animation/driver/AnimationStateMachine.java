package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.model.FiniteStateMachine;
import com.yesman.akythera.core.math.model.StateInstance;
import com.yesman.akythera.core.math.model.StateModel;
import com.yesman.akythera.core.math.model.Transition;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A state machine that runs on the [#owner]'s lifecycle to play its locomotion.
public final class AnimationStateMachine<T extends EntityPatch<Entity>>
    implements FiniteStateMachine<T, FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance>, AnimationDriver
{
    private final T owner;
    private final StateModel<FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance, AnimationStateMachine<T>> stateModel;
    private FSMAnimationState state = AkytheraAnimationState.ENTRY;

    private boolean firstCome;

    @NotNull
    private AnimationDriver lastDriver;

    public AnimationStateMachine(T owner, StateModel.StateModelBuilder<T, FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance, AnimationStateMachine<T>> stateModelBuilder) {
        this.owner = owner;

        stateModel = stateModelBuilder.build(this);

        // Initialize with entry driver, an empty driver
        lastDriver = stateModel.getStateInstance(state).animDriver;
    }

    private AnimationStateInstance createInstance(List<Transition<?, ?, ?>> transitions, FSMAnimationState state, AnimationDriver animDriver) {
        return new AnimationStateInstance(transitions, state, animDriver);
    }

    @Override
    public FSMAnimationState getState() {
        return state;
    }

    @Override
    public StateModel<FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance, AnimationStateMachine<T>> getStateModel() {
        return stateModel;
    }

    @Override
    public T getOwner() {
        return owner;
    }

    @Override
    public FSMAnimationState getEntryState() {
        return AkytheraAnimationState.ENTRY;
    }

    @Override
    public FSMAnimationState getExitState() {
        return AkytheraAnimationState.EXIT;
    }

    @Override
    public void setState(FSMAnimationState state) {
        this.state = state;
    }

    @Override
    public Pose getPose() {
        AnimationDriver animDriver = getCurrentStateDriver();
        return Pose.blendPoses(lastDriver.getPose(), lastDriver.getWeight(), animDriver.getPose(), animDriver.getWeight());
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return getCurrentStateDriver().getPlayingAnimation();
    }

    /// Updates animation time of each state's animation driver.
    @Override
    public void update(final float frameTime) {
        // Updates the previous driver when fading is progressing
        if (0.0F < lastDriver.getWeight()) {
            lastDriver.update(frameTime);
        }

        getCurrentStateDriver().update(frameTime);
    }

    /// Implementation of [AnimationDriver]: tick the machine as driver
    @Override
    public void tick(final EntityPatch<Entity> owner) {
        tick();
    }

    @Override
    public float getTime() {
        return getCurrentStateDriver().getTime();
    }

    /// Starts the state machine only when it's not running.
    @Override
    public void start() {
        if (getState() == AkytheraAnimationState.EXIT) {
            setState(AkytheraAnimationState.ENTRY);
        }
    }

    /// Called automatically on state being [AkytheraAnimationState#EXIT].
    @Override
    public void terminate() {
        if (getState() != AkytheraAnimationState.EXIT) {
            getStateModel().getStateInstance(getState()).animDriver.terminate();
        }
    }

    /// Weight starts fading-in and fading-out on entry and exit states and will keep
    /// 1.0 when state transition occurs.
    @Override
    public float getWeight() {
        // Judges whether it's a first played driver by a stateful variable. The entry
        // state is removed as soon as the machine starts.
        if (firstCome) {
            return getCurrentStateDriver().getWeight();
        }

        // Gets weight from last recorded anim driver when the state is exited
        if (getState() == AkytheraAnimationState.EXIT) {
            return lastDriver.getWeight();
        }

        return 1.0F;
    }

    @Override
    public boolean isFinished() {
        return getCurrentStateDriver().isFinished();
    }

    @Override
    public boolean fadingInOrOut() {
        return getCurrentStateDriver().fadingInOrOut();
    }

    private AnimationDriver getCurrentStateDriver() {
        return stateModel.getStateInstance(getState()).animDriver;
    }

    public static <T extends EntityPatch<Entity>> StateModel.StateModelBuilder<T, FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance, AnimationStateMachine<T>> stateModelBuilder() {
        return StateModel.builder(
            new AnimationInstanceStateBuilder<>(AkytheraAnimationState.ENTRY, new EmptyAnimationDriver()),
            new AnimationInstanceStateBuilder<>(AkytheraAnimationState.EXIT, new EmptyAnimationDriver())
        );
    }

    public static <T extends EntityPatch<Entity>> AnimationInstanceStateBuilder<T> stateInstanceBuilder(FSMAnimationState state, AnimationDriver animDriver) {
        return new AnimationInstanceStateBuilder<>(state, animDriver);
    }

    public class AnimationStateInstance extends StateInstance<FSMAnimationState> {
        private final FSMAnimationState state;

        /// Null for entry, exit states
        private final AnimationDriver animDriver;

        private AnimationStateInstance(
            final List<Transition<?, ?, ?>> transitions,
            final FSMAnimationState state,
            final AnimationDriver animDriver
        ) {
            super(transitions);

            this.state = state;
            this.animDriver = animDriver;
        }

        /// State started by transition, reset the fade-in timer
        @Override
        public void onStart() {
            animDriver.start();
        }

        /// Set the last pose to fade out. If a new state is set by transition, fade time will be reset to 0 by [#onStart]
        @Override
        public void onStop() {
            // When entry state stops (i.e. when the machine starts) record "first come" true.
            firstCome = state == AkytheraAnimationState.ENTRY;
            animDriver.terminate();

            // Record the last driver
            lastDriver = animDriver;
        }

        /// Do nothing in here, poses are updated by [AnimationStateMachine#update]
        @Override
        protected boolean tick() {
            // Update the machine-in-machine
            animDriver.tick(owner);

            return true;
        }

        @Override
        public FSMAnimationState toSignature() {
            return state;
        }
    }

    public static final class AnimationInstanceStateBuilder<T extends EntityPatch<Entity>>
        extends StateModel.StateInstanceBuilder<FSMAnimationState, AnimationStateMachine<T>.AnimationStateInstance, AnimationStateMachine<T>>
    {
        private final FSMAnimationState state;
        private final AnimationDriver animDriver;

        private AnimationInstanceStateBuilder(final FSMAnimationState state, final AnimationDriver animDriver) {
            this.state = state;
            this.animDriver = animDriver;
        }

        @Override
        protected AnimationStateMachine<T>.AnimationStateInstance makeInstance(AnimationStateMachine<T> owner) {
            return owner.createInstance(transitions, state, animDriver);
        }
    }
}
