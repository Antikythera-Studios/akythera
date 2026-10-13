package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// Runs [#whenSucceeded] when the value of [#object] is a [#type], and [#whenFailed] otherwise. Equals to
/// `if (object instanceof Type castName) {...} else {...}`
///
/// @param type          the type the value is tested against
/// @param object        the value to cast
/// @param castName      the local the cast value is held in, set before [#whenSucceeded] runs
/// @param whenSucceeded nodes to run when the value is a [#type]
/// @param whenFailed    nodes to run when it is not, null included
public record CastNode(
    ScriptType type, PureNode object, String castName, List<FlowNode> whenSucceeded, List<FlowNode> whenFailed, int nodeId
) implements FlowNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public CastNode(
        final ScriptType type, final PureNode object, final String castName,
        final List<FlowNode> whenSucceeded, final List<FlowNode> whenFailed
    ) {
        this(type, object, castName, whenSucceeded, whenFailed, NO_NODE_ID);
    }

    public static final Codec<CastNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("type").forGetter(CastNode::type),
                PureNode.CODEC.fieldOf("object").forGetter(CastNode::object),
                Codec.STRING.fieldOf("cast_name").forGetter(CastNode::castName),
                FlowNode.CODEC.listOf().optionalFieldOf("when_succeeded", List.of()).forGetter(CastNode::whenSucceeded),
                FlowNode.CODEC.listOf().optionalFieldOf("when_failed", List.of()).forGetter(CastNode::whenFailed),
                Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(CastNode::nodeId)
            )
            .apply(instance, CastNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        Object value = object.execute(script, context);
        boolean succeeded = ParseUtils.box(type.type()).isInstance(value);

        context.recordInputs(value);

        if (succeeded) context.locals().setValue(castName, value);

        for (FlowNode node : succeeded ? whenSucceeded : whenFailed) {
            if (context.isReturning()) break;

            node.execute(script, context);
        }

        return context.done();
    }

    @Override
    public @NonNull String toString() {
        return "Cast To " + type.type().getSimpleName();
    }
}
