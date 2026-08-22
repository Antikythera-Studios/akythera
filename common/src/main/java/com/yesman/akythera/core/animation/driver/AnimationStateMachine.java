package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.model.FiniteStateMachine;
import com.yesman.akythera.core.math.model.StateInstance;
import com.yesman.akythera.core.math.model.StateModel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A state machine that runs on the [#owner]'s lifecycle to play its locomotion.
public final class AnimationStateMachine<O extends EntityPatch<Entity>>
    implements FiniteStateMachine<O, FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>>, AnimationDriver
{
    private final O owner;
    private final StateModel<FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>, AnimationStateMachine<O>> stateModel;

    private FSMAnimationState state = AkytheraAnimationState.ENTRY;

    @NonNull
    private AnimationDriver currentDriver;

    public AnimationStateMachine(O owner, StateModel.StateModelBuilder<O, FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>, AnimationStateMachine<O>> stateModelBuilder) {
        this.owner = owner;

        stateModel = stateModelBuilder.build(this);

        // Initialize with entry driver, an empty driver
        currentDriver = stateModel.getStateInstance(state).animDriver;
    }

    private AnimationStateInstance createInstance(List<AnimationStateTransition<O>> transitions, FSMAnimationState state, AnimationDriver animDriver) {
        return new AnimationStateInstance(transitions, state, animDriver);
    }

    @Override
    public FSMAnimationState getState() {
        return state;
    }

    @Override
    public StateModel<FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>, AnimationStateMachine<O>> getStateModel() {
        return stateModel;
    }

    @Override
    public O getOwner() {
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
        return currentDriver.getPose();
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return getCurrentStateDriver().getPlayingAnimation();
    }

    /// Updates animation time of each state's animation driver.
    @Override
    public void update(final float frameTime) {
        currentDriver.update(frameTime);
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
            getStateModel().getStateInstance(getState()).animDriver.start();
        }
    }

    /// Called automatically on state being [AkytheraAnimationState#EXIT].
    @Override
    public void terminate() {
        if (getState() != AkytheraAnimationState.EXIT) {
            getStateModel().getStateInstance(getState()).animDriver.terminate();
            setState(AkytheraAnimationState.EXIT);
        }
    }

    /// Weight starts fading-in and fading-out on entry and exit states and will keep
    /// 1.0 when state transition occurs.
    @Override
    public float getWeight() {
        return getCurrentStateDriver().getWeight();
    }

    @Override
    public boolean isFinished() {
        return getCurrentStateDriver().isFinished();
    }

    @Override
    public boolean fadingInOrOut() {
        return getCurrentStateDriver().fadingInOrOut();
    }

    @Override
    public float getFadeTime() {
        return getCurrentStateDriver().getFadeTime();
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        getCurrentStateDriver().syncDriverTo(source);
    }

    private AnimationDriver getCurrentStateDriver() {
        return stateModel.getStateInstance(getState()).animDriver;
    }

    /// The [AnimationDriver] `stateInstance` plays. Empty for a state instance this machine did not
    /// build -- a transition check is typed against [StateInstance], not this machine's own instance.
    public static AnimationDriver driverOf(final StateInstance<?, ?> stateInstance) {
        return stateInstance instanceof AnimationStateMachine<?>.AnimationStateInstance instance
            ? instance.animDriver
            : EmptyAnimationDriver.INSTANCE;
    }

    public static <O extends EntityPatch<Entity>> StateModel.StateModelBuilder<O, FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>, AnimationStateMachine<O>> stateModelBuilder() {
        return StateModel.builder(
            new AnimationInstanceStateBuilder<>(AkytheraAnimationState.ENTRY, EmptyAnimationDriver.INSTANCE),
            new AnimationInstanceStateBuilder<>(AkytheraAnimationState.EXIT, EmptyAnimationDriver.INSTANCE)
        );
    }

    public static <T extends EntityPatch<Entity>> AnimationInstanceStateBuilder<T> stateInstanceBuilder(FSMAnimationState state, AnimationDriver animDriver) {
        return new AnimationInstanceStateBuilder<>(state, animDriver);
    }

    // Called by `AnimationStateTransition`.
    void transferDriver(final AnimationStateMachine<O>.AnimationStateInstance nextState, final float blendTime, final boolean transferElapsedTime) {
        if (transferElapsedTime) {
            nextState.animDriver.syncDriverTo(currentDriver);
        }

        if (currentDriver instanceof DriverTransfer) {
            // If a transition is already on going, only capture the pose and blend the pose
            // with next anim driver, preventing nested drivers when state changes quickly.
            Pose currentPose = currentDriver.getPose();
            currentDriver = new PoseToDriverTransfer(currentPose, nextState.animDriver, blendTime, () -> currentDriver = nextState.animDriver);
        } else {
            currentDriver = new DriverToDriverTransfer(currentDriver, nextState.animDriver, blendTime, () -> currentDriver = nextState.animDriver);
        }
    }

    public class AnimationStateInstance extends StateInstance<FSMAnimationState, AnimationStateTransition<O>> {
        private final FSMAnimationState state;

        /// Null for entry, exit states
        private final AnimationDriver animDriver;

        private AnimationStateInstance(
            final List<AnimationStateTransition<O>> transitions,
            final FSMAnimationState state,
            final AnimationDriver animDriver
        ) {
            super(transitions);

            this.state = state;
            this.animDriver = animDriver;
        }

        /// State started by transition, reset the fade-in timer
        @Override
        protected void onStart() {
            animDriver.start();
        }

        /// Set the last pose to fade out. If a new state is set by transition, fade time will be reset to 0 by [#onStart]
        @Override
        protected void onStop() {
            animDriver.terminate();
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

        public AnimationStateMachine<O> getMachine() {
            return AnimationStateMachine.this;
        }
    }

    public static final class AnimationInstanceStateBuilder<O extends EntityPatch<Entity>>
        extends StateModel.StateInstanceBuilder<FSMAnimationState, AnimationStateMachine<O>.AnimationStateInstance, AnimationStateTransition<O>, AnimationStateMachine<O>>
    {
        private final FSMAnimationState state;
        private final AnimationDriver animDriver;

        private AnimationInstanceStateBuilder(final FSMAnimationState state, final AnimationDriver animDriver) {
            this.state = state;
            this.animDriver = animDriver;
        }

        @Override
        protected AnimationStateMachine<O>.AnimationStateInstance makeInstance(AnimationStateMachine<O> owner) {
            return owner.createInstance(transitions, state, animDriver);
        }
    }
}
