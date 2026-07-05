package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

public record ScriptVariableSetterNode(String variableName, PureNode valueProvider) implements FlowNode {
    public static final Codec<ScriptVariableSetterNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ScriptVariableSetterNode::variableName),
                    PureNode.CODEC.fieldOf("value").forGetter(ScriptVariableSetterNode::valueProvider)
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
