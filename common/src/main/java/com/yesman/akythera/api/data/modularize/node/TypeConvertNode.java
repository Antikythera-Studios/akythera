package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import org.jspecify.annotations.NonNull;

import java.util.function.Function;

public record TypeConvertNode(ScriptType from, ScriptType to, PureNode input, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public TypeConvertNode(final ScriptType from, final ScriptType to, final PureNode input) {
        this(from, to, input, NO_NODE_ID);
    }

    public static final Codec<TypeConvertNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("from")
                        .forGetter(TypeConvertNode::from),
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("to")
                        .forGetter(TypeConvertNode::to),
                    PureNode.CODEC.fieldOf("from_value").forGetter(TypeConvertNode::input),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(TypeConvertNode::nodeId)
                )
                .apply(instance, TypeConvertNode::new)
        );

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);

        @SuppressWarnings("unchecked")
        Function<Object, Object> narrowing = (Function<Object, Object>) from.implicitlyCastableTypes().get(to.type());

        if (narrowing != null) return context.returnVal(narrowing.apply(input.execute(script, context)));

        // A widening cast is the reverse of the target's narrowing one (e.g. float -> double)
        if (to.implicitlyCastableTypes().containsKey(from.type()) && input.execute(script, context) instanceof Number number) {
            Class<?> type = to.type();
            Object widened;

            if (type == Double.class) widened = number.doubleValue();
            else if (type == Float.class) widened = number.floatValue();
            else if (type == Long.class) widened = number.longValue();
            else if (type == Integer.class) widened = number.intValue();
            else widened = number.shortValue();

            return context.returnVal(widened);
        }

        throw new IllegalStateException("Can't convert from " + from.type().getSimpleName() + " to " + to.type().getSimpleName());
    }

    @Override
    public @NonNull String toString() {
        return "Convert " + from.type().getSimpleName() + " to " + to.type().getSimpleName();
    }
}
