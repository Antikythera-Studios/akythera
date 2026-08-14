package com.yesman.akythera.api.data.modularize.node;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

import java.util.function.Function;

/// Pure node that returns one bound [Mesh] accessor. The asset rides its id and loads on the first
/// [AssetAccessor#get].
public record MeshAccessorNode(AssetAccessor<? extends Mesh<?>> mesh, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public MeshAccessorNode(final AssetAccessor<? extends Mesh<?>> mesh) {
        this(mesh, NO_NODE_ID);
    }

    /// The bare mesh id, which is the whole node when it carries no [#nodeId].
    private static final Codec<MeshAccessorNode> PLAIN_CODEC =
        MeshManager.CODEC.xmap(MeshAccessorNode::new, MeshAccessorNode::mesh);

    /// The expanded form, written only for a node that carries an id.
    private static final Codec<MeshAccessorNode> IDENTIFIED_CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    MeshManager.CODEC.fieldOf("mesh").forGetter(MeshAccessorNode::mesh),
                    Codec.INT.fieldOf("node_id").forGetter(MeshAccessorNode::nodeId)
                )
                .apply(instance, MeshAccessorNode::new)
        );

    public static final Codec<MeshAccessorNode> CODEC =
        Codec.either(IDENTIFIED_CODEC, PLAIN_CODEC).xmap(
            either -> either.map(Function.identity(), Function.identity()),
            node -> node.nodeId() == NO_NODE_ID ? Either.right(node) : Either.left(node)
        );

    @Override
    public <R> R execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);
        return context.returnVal(mesh);
    }

    @Override
    public @NonNull String toString() {
        return "Mesh";
    }
}
