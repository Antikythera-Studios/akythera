package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// A node that returns null. Fills an unwired input of a type that has no literal.
public final class NullNode implements PureNode {
    public static final NullNode INSTANCE = new NullNode();
    public static final Codec<NullNode> CODEC = MapCodec.unitCodec(INSTANCE);

    private NullNode() {}

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(null);
    }

    @Override
    public @NonNull String toString() {
        return "Null";
    }
}
