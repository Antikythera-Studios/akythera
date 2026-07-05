package com.yesman.akythera.api.data.modularize.node;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/// Pure node that returns literal value
public record LiteralNode<T>(ScriptType type, T literal) implements PureNode {
    public static final Codec<LiteralNode<?>> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("type")
                        .forGetter(LiteralNode::type),
                    Codec.PASSTHROUGH.optionalFieldOf("value").forGetter(ParseUtils.provideOptional(LiteralNode::valueAsDynamic))
                )
                .apply(instance, LiteralNode::decode)
        );

    @SuppressWarnings("unchecked")
    private static <V> LiteralNode<V> decode(final ScriptType type, final Optional<Dynamic<?>> value) {
        V literal = value.isEmpty() || type.codec() == null
            ? null
            : ((Codec<V>) type.codec()).parse(value.get()).getOrThrow();

        return new LiteralNode<>(type, literal);
    }

    @Override
    public <R> R execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(literal);
    }

    // For codec
    private Dynamic<?> valueAsDynamic() {
        return type.codec() == null ? null : new Dynamic<> (JsonOps.INSTANCE, encode().getOrThrow());
    }

    @SuppressWarnings("unchecked")
    private DataResult<JsonElement> encode() {
        assert type.codec() != null;
        return ((Codec<T>)type.codec()).encodeStart(JsonOps.INSTANCE, literal());
    }

    @Override
    public @NonNull String toString() {
        return "Literal";
    }
}
