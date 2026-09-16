package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// Branch two nodes by `boolFunction`. Equals to `if-else` statement
///
/// @param boolFunction criteria inside `if`. Must return `boolean`.
/// @param whenTrue     node id to jump when `boolFunction` returns `true` (-1 when nothing to connect)
/// @param whenFalse    node id to jump when `boolFunction` returns `false` (-1 when nothing to connect)
public record BranchNode(
    PureNode boolFunction, List<FlowNode> whenTrue, List<FlowNode> whenFalse, int nodeId
) implements FlowNode {
    /// A node with no canvas row behind it — the builders, and any hand-assembled tree.
    public BranchNode(
        final PureNode boolFunction, final List<FlowNode> whenTrue, final List<FlowNode> whenFalse
    ) {
        this(boolFunction, whenTrue, whenFalse, NO_NODE_ID);
    }

    public static final Codec<BranchNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                PureNode.CODEC.fieldOf("boolFunction").forGetter(BranchNode::boolFunction),
                FlowNode.CODEC.listOf().fieldOf("whenTrue").forGetter(BranchNode::whenTrue),
                FlowNode.CODEC.listOf().fieldOf("whenFalse").forGetter(BranchNode::whenFalse),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(BranchNode::nodeId)
            )
            .apply(instance, BranchNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        boolean b = Boolean.TRUE.equals(boolFunction.execute(script, context));

        for (FlowNode node : b ? whenTrue : whenFalse) {
            if (context.isReturning()) break;

            node.execute(script, context);
        }

        // Closing here keeps the frame open across the arm, so the statements it chose are recorded
        // inside it. Branching node doesn't return any value, and done() yields null.
        return context.done();
    }

    public static BranchNode.Builder builder(final FlowFunctionNode boolFunction) {
        return new BranchNode.Builder(boolFunction);
    }

    public static final class Builder {
        final FlowFunctionNode boolFunction;
        final List<FlowNode> whenTrue = new ArrayList<>();
        final List<FlowNode> whenFalse = new ArrayList<>();

        private Builder(final FlowFunctionNode boolFunction) {
            if (boolFunction.function().functionSignature().returnType() != boolean.class
                && boolFunction.function().functionSignature().returnType() != Boolean.class)
            {
                throw new IllegalArgumentException("Only boolean|Boolean return types are allowed for Branching Node");
            }

            this.boolFunction = boolFunction;
        }

        public Builder addTaskWhenTrue(final FlowNode node) {
            whenTrue.add(node);
            return this;
        }

        public Builder addTaskWhenFalse(final FlowNode node) {
            whenFalse.add(node);
            return this;
        }
    }

    @Override
    public @NonNull String toString() {
        return "Branching Flow";
    }
}
