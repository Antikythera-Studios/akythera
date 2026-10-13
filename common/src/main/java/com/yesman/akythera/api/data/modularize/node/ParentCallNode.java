package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// Calls the implementation the running function overrides, with the arguments the function was called with.
/// As a statement, its value is dropped.
public record ParentCallNode(int nodeId) implements FlowNode, PureNode {
    public static final Codec<ParentCallNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ParentCallNode::nodeId)
            ).apply(instance, ParentCallNode::new)
    );

    @Override
    public <T> @Nullable T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Supplier<?> parent = context.locals().getValue(BlueprintExecutor.PARENT_LOCAL);

        return context.returnVal(parent.get());
    }

    @Override
    public @NonNull String toString() {
        return "Parent Call";
    }
}
