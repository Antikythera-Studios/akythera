package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.HorizontalWidgetContainer;
import com.yesman.akythera.client.api.gui.VerticalWidgetContainer;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;

import java.util.Map;

/// Builds a [HorizontalWidgetContainer] or a [VerticalWidgetContainer] laying `children` out one after another,
/// by their [WidgetComponentBlueprint#order]. One component type per direction, sharing this record.
///
/// @param padding space between the children and around them along the axis, in pixels
public record LinearContainerBlueprint(
    boolean vertical,
    WidgetLayout layout,
    int order,
    int padding,
    Map<String, ComponentBlueprint<?, ?>> children,
    boolean enabled
) implements WidgetComponentBlueprint {
    public static final MapCodec<LinearContainerBlueprint> HORIZONTAL_CODEC = codec(false);
    public static final MapCodec<LinearContainerBlueprint> VERTICAL_CODEC = codec(true);

    private static MapCodec<LinearContainerBlueprint> codec(final boolean vertical) {
        return RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    LAYOUT_CODEC.forGetter(LinearContainerBlueprint::layout),
                    ORDER_CODEC.forGetter(LinearContainerBlueprint::order),
                    Codec.intRange(0, 4096).optionalFieldOf("padding", 0).forGetter(LinearContainerBlueprint::padding),
                    CHILDREN_CODEC.optionalFieldOf("components", Map.of()).forGetter(LinearContainerBlueprint::children),
                    ENABLED_CODEC.forGetter(LinearContainerBlueprint::enabled)
                ).apply(instance, (layout, order, padding, children, enabled) -> new LinearContainerBlueprint(vertical, layout, order, padding, children, enabled))
        );
    }

    @Override
    public boolean acceptsInput() {
        return WidgetComponentBlueprint.anyAcceptsInput(children);
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.linearContainer(this);
    }

    @Override
    public MapCodec<LinearContainerBlueprint> mapCodec() {
        return vertical ? VERTICAL_CODEC : HORIZONTAL_CODEC;
    }
}
