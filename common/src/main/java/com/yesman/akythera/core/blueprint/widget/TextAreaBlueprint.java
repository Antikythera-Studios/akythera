package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.TextArea;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;

import java.util.List;
import java.util.Map;

/// Builds a [TextArea] showing `text`. It takes input either way: an editable one takes typing, and every one
/// scrolls.
public record TextAreaBlueprint(WidgetLayout layout, int order, LocalizedMessage text, boolean editable, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<TextAreaBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(TextAreaBlueprint::layout),
                ORDER_CODEC.forGetter(TextAreaBlueprint::order),
                LocalizedMessage.CODEC.optionalFieldOf("text", LocalizedMessage.EMPTY).forGetter(TextAreaBlueprint::text),
                Codec.BOOL.optionalFieldOf("editable", false).forGetter(TextAreaBlueprint::editable),
                ENABLED_CODEC.forGetter(TextAreaBlueprint::enabled)
            ).apply(instance, TextAreaBlueprint::new)
    );

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(
            ON_CHANGED, WidgetComponentBlueprint.changedArguments(AkytheraScriptTypes.STRING),
            ON_COMMITTED, WidgetComponentBlueprint.committedArguments(AkytheraScriptTypes.STRING)
        );
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.textArea(this);
    }

    @Override
    public MapCodec<TextAreaBlueprint> mapCodec() {
        return CODEC;
    }
}
