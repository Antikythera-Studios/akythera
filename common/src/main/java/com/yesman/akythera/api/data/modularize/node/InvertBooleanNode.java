package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// A pure node that negates its boolean child (logical NOT). Used by transition-condition graphs in
/// the Anim Graph's state machines (e.g. invert an "is moving" check).
public record InvertBooleanNode(PureNode child, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public InvertBooleanNode(final PureNode child) {
        this(child, NO_NODE_ID);
    }

    public static final Codec<InvertBooleanNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                PureNode.CODEC.fieldOf("child").forGetter(InvertBooleanNode::child),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(InvertBooleanNode::nodeId)
            ).apply(instance, InvertBooleanNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(!(Boolean)child.execute(script, context));
    }

    @Override
    public @NonNull String toString() {
        return "Invert bool";
    }
}