package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/// Pure node that returns the [ResourceKey] [#id] names in the registry of [#type]. The entry is not looked up.
///
/// @param type a [ScriptType#registryEntry] type
public record RegistryKeyNode(ScriptType type, Identifier id, int nodeId) implements PureNode {
    /// A node with no canvas row behind it.
    public RegistryKeyNode(final ScriptType type, final Identifier id) {
        this(type, id, NO_NODE_ID);
    }

    public static final Codec<RegistryKeyNode> CODEC =
        RecordCodecBuilder.<RegistryKeyNode>create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("type")
                        .forGetter(RegistryKeyNode::type),
                    Identifier.CODEC.fieldOf("id").forGetter(RegistryKeyNode::id),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(RegistryKeyNode::nodeId)
                )
                .apply(instance, RegistryKeyNode::new)
        )
        .validate(node -> node.type().registry() == null
            ? DataResult.error(() -> node.type().type().getName() + " is no registry entry type")
            : DataResult.success(node));

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        return context.returnVal(ResourceKey.create((ResourceKey) type.registry(), id));
    }

    @Override
    public @NonNull String toString() {
        return "Registry Key";
    }
}
