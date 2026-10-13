package com.yesman.akythera.api.data.modularize.node.animgraph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationStateMachine;
import com.yesman.akythera.core.animation.driver.AnimationStateTransition;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.model.StateInstance;
import com.yesman.akythera.core.math.model.StateModel;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.List;
import java.util.function.BiFunction;

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
    /// Context local a transition condition reads the driver of the state it leaves through. Bound by
    /// [#evaluate] on each check and read by [SourceDriverNode].
    static final String SOURCE_DRIVER = "stateDriver";

    /// A transition to `to` gated by `condition`; the `from` is the owning state (or Entry).
    /// `blendTime`, `transferElapsedTime` and `transitionPriority` are the like-named
    /// [AnimationStateTransition] components. `canvas` is the editor canvas `condition` was compiled
    /// from, which is what identifies its rows.
    public record TransitionEntry(
        FSMAnimationState to, PureNode condition,
        float blendTime, boolean transferElapsedTime, int transitionPriority,
        String canvas
    ) {
        /// A transition with no editor canvas behind it — any hand-assembled machine.
        public TransitionEntry(
            final FSMAnimationState to, final PureNode condition,
            float blendTime, boolean transferElapsedTime, int transitionPriority
        ) {
            this(to, condition, blendTime, transferElapsedTime, transitionPriority, ScriptNode.NO_CANVAS);
        }

        public static final Codec<TransitionEntry> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    FSMAnimationState.ENUM_MANAGER.getNameCodec().fieldOf("to").forGetter(TransitionEntry::to),
                    PureNode.CODEC.fieldOf("condition").forGetter(TransitionEntry::condition),
                    Codec.FLOAT.fieldOf("blend_time").forGetter(TransitionEntry::blendTime),
                    Codec.BOOL.optionalFieldOf("transfer_elapsed_time", false).forGetter(TransitionEntry::transferElapsedTime),
                    Codec.INT.optionalFieldOf("transition_priority", 0).forGetter(TransitionEntry::transitionPriority),
                    Codec.STRING.optionalFieldOf("canvas", ScriptNode.NO_CANVAS).forGetter(TransitionEntry::canvas)
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
        ComponentAttachable owner = animator.getOwner();
        StateModel.StateModelBuilder builder = AnimationStateMachine.stateModelBuilder();

        // Entry transitions attach to the entry state (the builder cursor starts there).
        for (TransitionEntry t : entryTransitions) {
            builder.transitTo(new AnimationStateTransition(
                t.to(), check(script, t), null, 0,
                t.blendTime(), t.transferElapsedTime(), t.transitionPriority()));
        }

        for (StateEntry s : states) {
            AnimationDriver driver = s.poseDriver().execute(script, context);
            builder.addState(s.state(), AnimationStateMachine.stateInstanceBuilder(s.state(), driver));

            for (TransitionEntry t : s.transitions()) {
                builder.transitTo(new AnimationStateTransition(
                t.to(), check(script, t), null, 0,
                t.blendTime(), t.transferElapsedTime(), t.transitionPriority()));
            }
        }

        return (T) new AnimationStateMachine(owner, builder);
    }

    /// A live transition check running `entry`'s condition through [#evaluate].
    @SuppressWarnings("rawtypes")
    private static BiFunction<AnimationStateMachine.AnimationStateInstance, AnimationStateMachine.AnimationStateInstance, Boolean> check(
        final DataDrivenScript<?, ?> script, final TransitionEntry entry
    ) {
        return (from, _) -> evaluate(script, entry, from);
    }

    /// Evaluates `entry`'s condition against the script's current variables, with the driver of the
    /// state being left bound to [#SOURCE_DRIVER]. An empty / non-boolean condition (nothing wired)
    /// reads as an unconditional (always-true) transition, a throwing one as false.
    ///
    /// Sited at [TransitionEntry#canvas], and recorded into that canvas' live record.
    public static boolean evaluate(
        final DataDrivenScript<?, ?> script, final TransitionEntry entry, final StateInstance<?, ?> from
    ) {
        ScriptVariables variables = script.getScriptVariable();
        variables.setValue(SOURCE_DRIVER, AnimationStateMachine.driverOf(from));

        ExecutionContext context = ScriptRecorder.liveContext(ScriptCaller.of(script, entry.canvas()), variables);

        try {
            Object value = entry.condition().execute(script, context);

            return !(value instanceof Boolean bool) || bool;
        } catch (Throwable throwable) {
            ScriptRecorder.recordFailure(context, throwable);

            return false;
        }
    }
}