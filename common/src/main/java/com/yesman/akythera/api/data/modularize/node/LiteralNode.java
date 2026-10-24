package com.yesman.akythera.api.data.modularize.node;

import com.google.common.base.Suppliers;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/// Pure node that returns literal value
///
/// @param literalValue the literal value, resolving to null when the node holds none
public record LiteralNode<T>(ScriptType type, Supplier<@Nullable T> literalValue, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — the compilers' typed defaults, and any hand-assembled tree.
    public LiteralNode(final ScriptType type, final @Nullable T literal) {
        this(type, literal, NO_NODE_ID);
    }

    public LiteralNode(final ScriptType type, final @Nullable T literal, final int nodeId) {
        this(type, () -> literal, nodeId);
    }

    public static final Codec<LiteralNode<?>> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("type")
                        .forGetter(LiteralNode::type),
                    Codec.PASSTHROUGH.optionalFieldOf("value").forGetter(ParseUtils.provideOptional(LiteralNode::valueAsDynamic)),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(LiteralNode::nodeId)
                )
                .apply(instance, LiteralNode::decode)
        );

    /// Decodes the serialized `value` on the first [#literal] call. A registry entry literal is looked up
    /// through [StaticRegistryAccess], which doesn't hold the registries being decoded while the data pack
    /// registries carrying this node load.
    @SuppressWarnings("unchecked")
    private static <V> LiteralNode<V> decode(final ScriptType type, final Optional<Dynamic<?>> value, final int nodeId) {
        Supplier<@Nullable V> literal = Suppliers.memoize(() -> value.isEmpty() || type.codec() == null
            ? null
            : ((Codec<V>) type.codec()).parse(value.get()).getOrThrow());

        return new LiteralNode<V>(type, literal, nodeId);
    }

    /// The literal value. Null when the node holds none.
    public @Nullable T literal() {
        return literalValue.get();
    }

    @Override
    public <R> R execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(literal());
    }

    // For codec. A null literal is left out of the json.
    private @Nullable Dynamic<?> valueAsDynamic() {
        return type.codec() == null || literal() == null ? null : new Dynamic<> (JsonOps.INSTANCE, encode().getOrThrow());
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
