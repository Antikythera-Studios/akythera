package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// Access to script variable via name
public record ScriptVariableGetterNode(String variableName, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public ScriptVariableGetterNode(final String variableName) {
        this(variableName, NO_NODE_ID);
    }

    public static final Codec<ScriptVariableGetterNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ScriptVariableGetterNode::variableName),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ScriptVariableGetterNode::nodeId)
                )
                .apply(instance, ScriptVariableGetterNode::new)
        );

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);
        return context.returnVal(script.getScriptVariable().getValue(variableName));
    }

    @Override
    public @NonNull String toString() {
        return "Member Variable " + variableName();
    }
}
