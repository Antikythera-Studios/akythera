package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;

/// A pure node that wraps a sub-flow in a [Runnable], so a flow output pin can satisfy a
/// `Runnable`-typed function parameter. This is what lets an [com.yesman.akythera.api.world.ability.AbilityTask]
/// run a piece of graph when it finishes or is interrupted.
///
/// The sub-flow runs many ticks after the entry point that built it returned, and every entry point
/// calls [ScriptVariables#reset] first, so the callback reads a snapshot of the locals rather than
/// the live set. Every adapter built during one run shares that snapshot.
///
/// `canvas` is the editor canvas [#child] was compiled from, which is what identifies its rows: the
/// callback runs after the entry point that built this node returned, so the row ids in it belong to
/// a graph the run itself no longer names.
public record CallbackAdapterNode(FlowNode child, String canvas) implements PureNode {
    /// A node with no editor canvas behind it — any hand-assembled tree.
    public CallbackAdapterNode(final FlowNode child) {
        this(child, NO_CANVAS);
    }

    public static final Codec<CallbackAdapterNode> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            FlowNode.CODEC.fieldOf("child").forGetter(CallbackAdapterNode::child),
            Codec.STRING.optionalFieldOf("canvas", NO_CANVAS).forGetter(CallbackAdapterNode::canvas)
        ).apply(instance, CallbackAdapterNode::new));

    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        ScriptVariables callbackLocals = context.callbackLocals();
        ScriptCaller caller = context.caller().at(canvas);

        return (T) (Runnable) () -> {
            ExecutionContext callback = ScriptRecorder.newContext(caller, callbackLocals);

            try {
                child.execute(script, callback);
            } catch (Throwable throwable) {
                ScriptRecorder.recordFailure(callback, throwable);
            }
        };
    }
}
