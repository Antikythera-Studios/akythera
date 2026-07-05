package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import org.jspecify.annotations.NonNull;

public record TypeConvertNode(ScriptType from, ScriptType to, PureNode input) implements PureNode {
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
                    PureNode.CODEC.fieldOf("from_value").forGetter(TypeConvertNode::input)
                )
                .apply(instance, TypeConvertNode::new)
        );

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);

        if (!from.explicitCastableTypes().containsKey(to.type())) {
            throw new IllegalStateException("Can't convert from " + from.type().getSimpleName() + " to " + to.type().getSimpleName());
        }

        return context.returnVal(from.explicitCastableTypes().get(to.type()).apply(input.execute(script, context)));
    }

    @Override
    public @NonNull String toString() {
        return "Convert " + from.type().getSimpleName() + " to " + to.type().getSimpleName();
    }
}
