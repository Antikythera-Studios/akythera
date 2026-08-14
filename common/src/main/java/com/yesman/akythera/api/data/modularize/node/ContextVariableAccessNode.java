package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// Returns local variable value. Internal node that is invisible in script editor.
public record ContextVariableAccessNode(String localVariableName, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — the compiler's hoisted reads, and any hand-assembled tree.
    public ContextVariableAccessNode(final String localVariableName) {
        this(localVariableName, NO_NODE_ID);
    }

    public static final Codec<ContextVariableAccessNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ContextVariableAccessNode::localVariableName),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ContextVariableAccessNode::nodeId)
                )
                .apply(instance, ContextVariableAccessNode::new)
        );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(context.locals().getValue(localVariableName()));
    }

    public @NonNull String toString() {
        return "Local variable " + localVariableName;
    }
}
