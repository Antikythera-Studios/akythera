package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.ColorPickerWidget;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;

import java.util.List;
import java.util.Map;

/// Builds a [ColorPickerWidget] starting at `color`.
public record ColorPickerBlueprint(WidgetLayout layout, int order, RGBA color, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<ColorPickerBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(ColorPickerBlueprint::layout),
                ORDER_CODEC.forGetter(ColorPickerBlueprint::order),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(ColorPickerBlueprint::color),
                ENABLED_CODEC.forGetter(ColorPickerBlueprint::enabled)
            ).apply(instance, ColorPickerBlueprint::new)
    );

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(ON_CHANGED, WidgetComponentBlueprint.changedArguments(AkytheraScriptTypes.COLOR));
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.colorPicker(this);
    }

    @Override
    public MapCodec<ColorPickerBlueprint> mapCodec() {
        return CODEC;
    }
}
