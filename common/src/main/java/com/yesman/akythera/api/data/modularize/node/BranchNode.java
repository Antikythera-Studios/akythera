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
public record BranchNode(PureNode boolFunction, List<FlowNode> whenTrue, List<FlowNode> whenFalse) implements FlowNode {
    public static final Codec<BranchNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                PureNode.CODEC.fieldOf("boolFunction").forGetter(BranchNode::boolFunction),
                FlowNode.CODEC.listOf().fieldOf("whenTrue").forGetter(BranchNode::whenTrue),
                FlowNode.CODEC.listOf().fieldOf("whenFalse").forGetter(BranchNode::whenFalse)
            )
            .apply(instance, BranchNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        boolean b = Boolean.TRUE.equals(boolFunction.execute(script, context));
        // Finish the nested structure after getting boolean param
        context.done();

        if (b) {
            for (FlowNode node : whenTrue) node.execute(script, context);
        } else {
            for (FlowNode node : whenFalse) node.execute(script, context);
        }

        // Branching node doesn't return any value
        return null;
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
                || boolFunction.function().functionSignature().returnType() != Boolean.class)
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
