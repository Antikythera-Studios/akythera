package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;

import java.util.Map;

/// Builds a [Grid] laying `children` out in cells, by their [WidgetComponentBlueprint#order].
///
/// @param cellWidth  cell width, in pixels
/// @param cellHeight cell height, in pixels
/// @param padding    space between the cells and around them, in pixels
public record GridBlueprint(
    WidgetLayout layout,
    int order,
    int cellWidth,
    int cellHeight,
    int padding,
    Map<String, ComponentBlueprint<?, ?>> children,
    boolean enabled
) implements WidgetComponentBlueprint {
    public static final MapCodec<GridBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(GridBlueprint::layout),
                ORDER_CODEC.forGetter(GridBlueprint::order),
                Codec.intRange(1, 4096).fieldOf("cell_width").forGetter(GridBlueprint::cellWidth),
                Codec.intRange(1, 4096).fieldOf("cell_height").forGetter(GridBlueprint::cellHeight),
                Codec.intRange(0, 4096).optionalFieldOf("padding", 0).forGetter(GridBlueprint::padding),
                CHILDREN_CODEC.optionalFieldOf("components", Map.of()).forGetter(GridBlueprint::children),
                ENABLED_CODEC.forGetter(GridBlueprint::enabled)
            ).apply(instance, GridBlueprint::new)
    );

    @Override
    public boolean acceptsInput() {
        return WidgetComponentBlueprint.anyAcceptsInput(children);
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.grid(this);
    }

    @Override
    public MapCodec<GridBlueprint> mapCodec() {
        return CODEC;
    }
}
