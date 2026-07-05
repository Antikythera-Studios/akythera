package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// Access to script variable via name
public record ScriptVariableGetterNode(String variableName) implements PureNode {
    public static final Codec<ScriptVariableGetterNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ScriptVariableGetterNode::variableName)
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
