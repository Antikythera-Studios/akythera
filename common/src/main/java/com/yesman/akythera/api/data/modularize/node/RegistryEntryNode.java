package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/// Pure node that returns the data pack registry entry [#id] names, looked up in the registry of [#type]
/// when it runs.
///
/// @param type a [ScriptType#registryEntry] type
public record RegistryEntryNode(ScriptType type, Identifier id, int nodeId) implements PureNode {
    /// A node with no canvas row behind it.
    public RegistryEntryNode(final ScriptType type, final Identifier id) {
        this(type, id, NO_NODE_ID);
    }

    public static final Codec<RegistryEntryNode> CODEC =
        RecordCodecBuilder.<RegistryEntryNode>create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                        .fieldOf("type")
                        .forGetter(RegistryEntryNode::type),
                    Identifier.CODEC.fieldOf("id").forGetter(RegistryEntryNode::id),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(RegistryEntryNode::nodeId)
                )
                .apply(instance, RegistryEntryNode::new)
        )
        .validate(node -> node.type().registry() == null
            ? DataResult.error(() -> node.type().type().getName() + " is no registry entry type")
            : DataResult.success(node));

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        ResourceKey<? extends Registry<?>> registry = type.registry();
        Object entry = StaticRegistryAccess.getRegistryAccess().getOrThrow(ResourceKey.create((ResourceKey) registry, id)).value();

        return context.returnVal(entry);
    }

    @Override
    public @NonNull String toString() {
        return "Registry Entry";
    }
}
