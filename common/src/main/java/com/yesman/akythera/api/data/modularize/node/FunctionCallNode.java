package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Calls the function `function` the running blueprint declares. As a statement, its value is dropped.
public record FunctionCallNode(String function, List<PureNode> arguments, int nodeId) implements FlowNode, PureNode {
    public static final Codec<FunctionCallNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("function").forGetter(FunctionCallNode::function),
                PureNode.CODEC.listOf().optionalFieldOf("arguments", List.of()).forGetter(FunctionCallNode::arguments),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(FunctionCallNode::nodeId)
            ).apply(instance, FunctionCallNode::new)
    );

    @Override
    public <T> @Nullable T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Object[] values = new Object[arguments.size()];
        ScriptVariables enclosingScope = context.beginCallbackScope();

        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = arguments.get(i).execute(script, context);
            }
        } finally {
            context.endCallbackScope(enclosingScope);
        }

        context.recordInputs(values);

        BlueprintExecutor executor = script.getBlueprintExecutor();

        if (executor == null) {
            throw new IllegalStateException(script.getClass().getSimpleName() + " runs no blueprint function");
        }

        return context.returnVal(executor.callFunction(function, values));
    }

    @Override
    public @NonNull String toString() {
        return "Call " + function;
    }
}
