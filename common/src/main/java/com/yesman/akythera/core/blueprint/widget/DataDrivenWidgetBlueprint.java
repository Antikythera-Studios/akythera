package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

import java.util.Map;

/// A widget container whose widgets and [BlueprintBody] are data, run by the data-driven script of its `usage`.
///
/// @param widgetEvents   the graphs the widgets' events run, by `<path>.<event>`, see
///                       [WidgetComponentBlueprint#events]
/// @param widgetBindings the graphs the widgets read their bound values from every tick, answering through a return
///                       node, by `<path>.<binding>`, see [WidgetComponentBlueprint#bindings]
public record DataDrivenWidgetBlueprint(
    WidgetUsage usage,
    ScriptFunction ctor,
    LocalizedMessage title,
    Map<String, ComponentBlueprint<?, ?>> components,
    BlueprintBody body,
    Map<String, FlowNode> widgetEvents,
    Map<String, FlowNode> widgetBindings
) implements WidgetBlueprint {
    public static final MapCodec<DataDrivenWidgetBlueprint> MAP_CODEC = RecordCodecBuilder.<DataDrivenWidgetBlueprint>mapCodec(
        instance ->
            instance.group(
                WidgetUsage.CODEC.fieldOf("usage").forGetter(DataDrivenWidgetBlueprint::usage),
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent").forGetter(DataDrivenWidgetBlueprint::ctor),
                LocalizedMessage.CODEC.optionalFieldOf("title", LocalizedMessage.EMPTY).forGetter(DataDrivenWidgetBlueprint::title),
                WidgetComponentBlueprint.CHILDREN_CODEC.optionalFieldOf("components", Map.of()).forGetter(DataDrivenWidgetBlueprint::components),
                BlueprintBody.MAP_CODEC.forGetter(DataDrivenWidgetBlueprint::body),
                Codec.unboundedMap(Codec.STRING, FlowNode.CODEC).optionalFieldOf("widget_events", Map.of()).forGetter(DataDrivenWidgetBlueprint::widgetEvents),
                Codec.unboundedMap(Codec.STRING, FlowNode.CODEC).optionalFieldOf("widget_bindings", Map.of()).forGetter(DataDrivenWidgetBlueprint::widgetBindings)
            ).apply(instance, DataDrivenWidgetBlueprint::new)
    ).validate(
        blueprint -> blueprint.usage != WidgetUsage.HUD || !WidgetComponentBlueprint.anyAcceptsInput(blueprint.components)
            ? DataResult.success(blueprint)
            : DataResult.error(() -> "A HUD widget blueprint holds a widget that takes input")
    );

    /// Builds the data-driven script of `usage` for `container`.
    @Override
    @SuppressWarnings("unchecked")
    public WidgetContainerScript instantiate(final ComponentAttachable container) {
        Registry<WidgetBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.WIDGET_KEY);

        return BlueprintWidgets.dataDrivenScript((Holder.Reference<DataDrivenWidgetBlueprint>)(Holder<?>)registry.wrapAsHolder(this), container);
    }

    @Override
    public MapCodec<DataDrivenWidgetBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
