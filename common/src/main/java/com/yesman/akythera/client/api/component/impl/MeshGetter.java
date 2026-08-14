package com.yesman.akythera.client.api.component.impl;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import org.jspecify.annotations.Nullable;

/// A component's authored mesh choice: a headless [PureNode] tree yielding the [Mesh] accessor to
/// draw, run against the [EntityPatch] that owns the component.
///
/// The owner rides the [#OWNER_PARAM] local of a context built once here, since a component's owner
/// never changes and [#resolve] runs several times a frame.
///
/// The authored form carries a null owner; the owning component binds one by rebuilding the getter
/// in its `instantiate`.
public final class MeshGetter {
    /// The context local [#node] reads the owning patch from.
    public static final String OWNER_PARAM = "owner";

    private final @Nullable PureNode node;
    private final ScriptNode.ExecutionContext context;

    public MeshGetter(final @Nullable PureNode node, final @Nullable EntityPatch<?> owner) {
        this.node = node;

        ScriptVariables locals = new ScriptVariables();
        locals.setValue(OWNER_PARAM, owner);

        this.context = new ScriptNode.ExecutionContext(locals);
    }

    /// The lowered tree, for the codecs that persist it. Null while nothing is authored.
    public @Nullable PureNode node() {
        return node;
    }

    /// The accessor the tree yields now, or null when there is no tree or it yields something that
    /// is not an accessor.
    @SuppressWarnings("unchecked")
    public @Nullable AssetAccessor<? extends Mesh<?>> resolve() {
        if (node == null) return null;

        Object value = node.execute(null, context);

        return value instanceof AssetAccessor<?> accessor
            ? (AssetAccessor<? extends Mesh<?>>)accessor
            : null;
    }
}
