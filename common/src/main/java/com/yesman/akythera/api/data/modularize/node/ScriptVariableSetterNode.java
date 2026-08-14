package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

public record ScriptVariableSetterNode(String variableName, PureNode valueProvider, int nodeId) implements FlowNode {
    /// A node with no canvas row behind it — the compiler's hoisted values, and any hand-assembled tree.
    public ScriptVariableSetterNode(final String variableName, final PureNode valueProvider) {
        this(variableName, valueProvider, NO_NODE_ID);
    }

    public static final Codec<ScriptVariableSetterNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ScriptVariableSetterNode::variableName),
                    PureNode.CODEC.fieldOf("value").forGetter(ScriptVariableSetterNode::valueProvider),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ScriptVariableSetterNode::nodeId)
                )
                .apply(instance, ScriptVariableSetterNode::new)
        );

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);

        return context.returnVal(
            script.getScriptVariable().setValue(variableName, valueProvider.execute(script, context))
        );
    }

    @Override
    public @NonNull String toString() {
        return "Member Variable Set " + variableName();
    }
}
