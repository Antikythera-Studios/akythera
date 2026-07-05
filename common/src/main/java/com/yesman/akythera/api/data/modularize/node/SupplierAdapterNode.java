package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.function.Supplier;

/// A pure node that wraps its child in a lazy [Supplier], so a value-producing [PureNode] can satisfy
/// a `Supplier`-typed function parameter. [FunctionNode] passes each parameter's *value*, not a thunk;
/// the Anim Graph compiler wraps a blendspace's `X` / `Y` float inputs in this node so they evaluate
/// **live** every time the driver samples them (mirroring `() -> moveYRot` in `PlayerAnimatorScript`).
public record SupplierAdapterNode(PureNode child) implements PureNode {
    public static final Codec<SupplierAdapterNode> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            PureNode.CODEC.fieldOf("child").forGetter(SupplierAdapterNode::child)
        ).apply(instance, SupplierAdapterNode::new));

    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        return (T) (Supplier<Object>) () -> child.execute(script, context);
    }
}