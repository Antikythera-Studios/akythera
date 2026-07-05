package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// A node that returns [DataDrivenScript#getScriptOwner]. Used for pure nodes
/// that belongs to script owner.
public class SelfNode implements PureNode {
    public static final SelfNode INSTANCE = new SelfNode();
    public static final Codec<SelfNode> CODEC = MapCodec.unitCodec(INSTANCE);

    private SelfNode() {}

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);
        return context.returnVal(script.getScriptOwner());
    }

    @Override
    public @NonNull String toString() {
        return "Self Reference";
    }
}
