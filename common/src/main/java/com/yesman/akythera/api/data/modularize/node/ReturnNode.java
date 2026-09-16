package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Ends the flow and yields `value`, which is what lets a function graph answer a question rather
/// than only act.
///
/// A graph with no return node, or one that never reaches it, leaves the caller its own default.
///
/// @param value the result, absent for a flow that returns nothing
public record ReturnNode(Optional<PureNode> value, int nodeId) implements FlowNode {
    /// A node with no canvas row behind it.
    public ReturnNode(final Optional<PureNode> value) {
        this(value, NO_NODE_ID);
    }

    public static final Codec<ReturnNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                PureNode.CODEC.optionalFieldOf("value").forGetter(ReturnNode::value),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ReturnNode::nodeId)
            )
            .apply(instance, ReturnNode::new)
    );

    @Override
    public <T> @Nullable T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Object result = value.isEmpty() ? null : value.get().execute(script, context);

        context.signalReturn(result);

        return context.returnVal(result);
    }

    @Override
    public @NonNull String toString() {
        return "Return";
    }
}
