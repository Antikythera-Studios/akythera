package com.yesman.akythera.api.data.modularize.node.animgraph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.EmptyAnimationDriver;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// A node that returns the [AnimationDriver] of the state a transition is leaving —
/// [AnimationStateMachineNode#SOURCE_DRIVER], which [AnimationStateMachineNode#evaluate] binds
/// before each check.
///
/// Empty wherever nothing bound that local: a pose canvas builds its drivers during `construct`,
/// when no state is being left, so the node reads an empty driver there rather than throwing.
public final class SourceDriverNode implements PureNode {
    public static final SourceDriverNode INSTANCE = new SourceDriverNode();
    public static final Codec<SourceDriverNode> CODEC = MapCodec.unitCodec(INSTANCE);

    private SourceDriverNode() {}

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        return context.returnVal(boundDriver(context));
    }

    /// The bound source driver, or an empty one outside a transition check.
    private static AnimationDriver boundDriver(final ExecutionContext context) {
        if (!context.locals().hasVariable(AnimationStateMachineNode.SOURCE_DRIVER)) {
            return EmptyAnimationDriver.INSTANCE;
        }

        return context.locals().getValue(AnimationStateMachineNode.SOURCE_DRIVER);
    }

    @Override
    public @NonNull String toString() {
        return "Source State Driver";
    }
}
