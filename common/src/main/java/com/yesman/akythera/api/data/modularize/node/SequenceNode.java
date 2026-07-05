package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// Sequentially execute the connected nodes
public record SequenceNode(List<FlowNode> executionBlocks) implements FlowNode {
    public static final Codec<SequenceNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                FlowNode.CODEC.listOf().fieldOf("execution_blocks").forGetter(SequenceNode::executionBlocks)
            )
            .apply(instance, SequenceNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        for (ScriptNode exeNode : executionBlocks) {
            exeNode.execute(script, context);
        }

        context.done();

        return null;
    }

    @Override
    public @NonNull String toString() {
        return "Sequence Flow";
    }

    public static final class Builder {
        private final List<List<ScriptNode>> executionBlocks = new ArrayList<> ();

        public Builder addNode(final ScriptNode node) {
            executionBlocks.getLast().add(node);
            return this;
        }

        public Builder newSequence() {
            executionBlocks.add(new ArrayList<>());
            return this;
        }
    }
}
