package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Writes `value` into the [BlueprintField] `field` of the script owner, answering the value written.
public record BlueprintFieldSetterNode(String field, PureNode value, int nodeId) implements FlowNode {
    public static final Codec<BlueprintFieldSetterNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("field").forGetter(BlueprintFieldSetterNode::field),
                PureNode.CODEC.fieldOf("value").forGetter(BlueprintFieldSetterNode::value),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(BlueprintFieldSetterNode::nodeId)
            ).apply(instance, BlueprintFieldSetterNode::new)
    );

    @Override
    public <T> @Nullable T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Object owner = script.getScriptOwner();
        Object written = value.execute(script, context);

        try {
            ScriptModularizeHelper.blueprintFieldOf(owner.getClass(), field).field().set(owner, written);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write the blueprint field " + field, e);
        }

        return context.returnVal(written);
    }

    @Override
    public @NonNull String toString() {
        return "Set " + field;
    }
}
