package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Reads the [BlueprintField] `field` of the script owner.
public record BlueprintFieldGetterNode(String field, int nodeId) implements PureNode {
    public static final Codec<BlueprintFieldGetterNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("field").forGetter(BlueprintFieldGetterNode::field),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(BlueprintFieldGetterNode::nodeId)
            ).apply(instance, BlueprintFieldGetterNode::new)
    );

    @Override
    public <T> @Nullable T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Object owner = script.getScriptOwner();

        try {
            return context.returnVal(ScriptModularizeHelper.blueprintFieldOf(owner.getClass(), field).field().get(owner));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read the blueprint field " + field, e);
        }
    }

    @Override
    public @NonNull String toString() {
        return "Get " + field;
    }
}
