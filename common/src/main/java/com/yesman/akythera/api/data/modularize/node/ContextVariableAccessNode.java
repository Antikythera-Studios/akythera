package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// Returns local variable value. Internal node that is invisible in script editor.
public record ContextVariableAccessNode(String localVariableName) implements PureNode {
    public static final Codec<ContextVariableAccessNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ContextVariableAccessNode::localVariableName)
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
