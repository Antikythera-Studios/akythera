package com.yesman.akythera.api.data.modularize.node.animgraph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationStateMachine;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akythera.core.math.model.StateModel;
import com.yesman.akythera.core.math.model.Transition;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.List;

/// A pure node that assembles an [AnimationStateMachine] (itself an [AnimationDriver]) from the Anim
/// Graph's `StateMachineData`. Executed during the script's `construct` (where the `animator` local is
/// set), so the machine is built **once** and stored as a layer; its `getPose()` then runs each frame.
///
/// Each state's `poseDriver` is its `{state}` pose sub-graph compiled to an [AnimationDriver]-valued
/// [PureNode]; each transition's `condition` is its `{from} to {to}` boolean sub-graph. Transition
/// checks evaluate the condition **live** against the script's current variables (the same channel
/// `update()` writes), so a state machine reacts each tick like a handwritten one. The entry state's
/// transitions are [#entryTransitions] — in `StateModelBuilder` the cursor starts on the entry state,
/// so they are emitted before any `addState`.
public record AnimationStateMachineNode(
    List<TransitionEntry> entryTransitions,
    List<StateEntry> states
) implements PureNode {

    /// A transition to `to` gated by `condition`; the `from` is the owning state (or Entry).
    public record TransitionEntry(FSMAnimationState to, PureNode condition) {
        public static final Codec<TransitionEntry> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    FSMAnimationState.ENUM_MANAGER.getNameCodec().fieldOf("to").forGetter(TransitionEntry::to),
                    PureNode.CODEC.fieldOf("condition").forGetter(TransitionEntry::condition)
                ).apply(instance, TransitionEntry::new)
        );
    }

    /// A state: its signature, the driver its pose graph compiles to, and the transitions leaving it.
    public record StateEntry(FSMAnimationState state, PureNode poseDriver, List<TransitionEntry> transitions) {
        public static final Codec<StateEntry> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    FSMAnimationState.ENUM_MANAGER.getNameCodec().fieldOf("state").forGetter(StateEntry::state),
                    PureNode.CODEC.fieldOf("pose_driver").forGetter(StateEntry::poseDriver),
                    TransitionEntry.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(StateEntry::transitions)
                ).apply(instance, StateEntry::new)
        );
    }

    public static final Codec<AnimationStateMachineNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                TransitionEntry.CODEC.listOf().optionalFieldOf("entry_transitions", List.of()).forGetter(AnimationStateMachineNode::entryTransitions),
                StateEntry.CODEC.listOf().optionalFieldOf("states", List.of()).forGetter(AnimationStateMachineNode::states)
            ).apply(instance, AnimationStateMachineNode::new)
    );

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        BlendingAnimator animator = context.locals().getValue("animator");
        EntityPatch<?> owner = animator.getOwner();
        StateModel.StateModelBuilder builder = AnimationStateMachine.stateModelBuilder();

        // Entry transitions attach to the entry state (the builder cursor starts there).
        for (TransitionEntry t : entryTransitions) {
            builder.transitTo(t.to(), check(script, t.condition()));
        }

        for (StateEntry s : states) {
            AnimationDriver driver = s.poseDriver().execute(script, context);
            builder.addState(s.state(), AnimationStateMachine.stateInstanceBuilder(s.state(), driver));

            for (TransitionEntry t : s.transitions()) {
                builder.transitTo(t.to(), check(script, t.condition()));
            }
        }

        return (T) new AnimationStateMachine(owner, builder);
    }

    /// A live transition check that evaluates `condition` against the script's current variables. An
    /// empty / non-boolean condition (nothing wired) reads as an unconditional (always-true) transition.
    private static Transition.TransitionCheck<?, ?> check(final DataDrivenScript<?, ?> script, final PureNode condition) {
        return (_, _) -> {
            Object value = condition.execute(script, new ExecutionContext(script.getScriptVariable()));
            return !(value instanceof Boolean bool) || bool;
        };
    }
}