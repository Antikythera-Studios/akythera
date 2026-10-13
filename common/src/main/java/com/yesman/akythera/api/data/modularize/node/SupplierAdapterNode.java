package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.function.Supplier;

/// A pure node that wraps its child in a lazy [Supplier], so a value-producing [PureNode] can satisfy
/// a `Supplier`-typed function parameter. [FunctionNode] passes each parameter's *value*, not a thunk;
/// the Anim Graph compiler wraps a blendspace's `X` / `Y` float inputs in this node so they evaluate
/// **live** every time the driver samples them (mirroring `() -> moveYRot` in `PlayerAnimatorScript`).
///
/// `canvas` is the editor canvas [#child] was compiled from, which is what identifies its rows: the
/// sample runs after the entry point that built this node returned, so the row ids in it belong to a
/// graph the run itself no longer names.
///
/// Each sample runs in a context of its own. A sample that throws is reported and answers what the
/// sample before it answered.
public record SupplierAdapterNode(PureNode child, String canvas) implements PureNode {
    /// What a sample answers when it threw before any sample answered. The adapter feeds blendspace
    /// coordinates.
    private static final Float NO_SAMPLE = 0.0F;

    /// A node with no editor canvas behind it — any hand-assembled tree.
    public SupplierAdapterNode(final PureNode child) {
        this(child, NO_CANVAS);
    }

    public static final Codec<SupplierAdapterNode> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            PureNode.CODEC.fieldOf("child").forGetter(SupplierAdapterNode::child),
            Codec.STRING.optionalFieldOf("canvas", NO_CANVAS).forGetter(SupplierAdapterNode::canvas)
        ).apply(instance, SupplierAdapterNode::new));

    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        ScriptCaller caller = context.caller().at(canvas);
        ScriptVariables locals = context.locals();
        Object[] lastAnswer = {NO_SAMPLE};

        return (T) (Supplier<Object>) () -> {
            ExecutionContext sample = ScriptRecorder.liveContext(caller, locals);

            try {
                lastAnswer[0] = child.execute(script, sample);
            } catch (Throwable throwable) {
                ScriptRecorder.recordFailure(sample, throwable);
            }

            return lastAnswer[0];
        };
    }
}
