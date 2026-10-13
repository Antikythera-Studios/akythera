package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.DragValueBox;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Builds a [DragValueBox] over a number between `min` and `max`.
///
/// @param theme the drag value box theme's name, or null for the default one
public record DragValueBoxBlueprint(
    WidgetLayout layout,
    int order,
    float min,
    float max,
    LocalizedMessage message,
    @Nullable Identifier theme,
    boolean enabled
) implements WidgetComponentBlueprint {
    public static final MapCodec<DragValueBoxBlueprint> CODEC = RecordCodecBuilder.<DragValueBoxBlueprint>mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(DragValueBoxBlueprint::layout),
                ORDER_CODEC.forGetter(DragValueBoxBlueprint::order),
                Codec.FLOAT.optionalFieldOf("min", 0.0F).forGetter(DragValueBoxBlueprint::min),
                Codec.FLOAT.optionalFieldOf("max", 1.0F).forGetter(DragValueBoxBlueprint::max),
                LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(DragValueBoxBlueprint::message),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(DragValueBoxBlueprint::theme)),
                ENABLED_CODEC.forGetter(DragValueBoxBlueprint::enabled)
            ).apply(instance, (layout, order, min, max, message, theme, enabled) -> new DragValueBoxBlueprint(layout, order, min, max, message, theme.orElse(null), enabled))
    ).validate(box -> box.min() < box.max() ? DataResult.success(box) : DataResult.error(() -> "Drag value box min " + box.min() + " is not below max " + box.max()));

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(
            ON_CHANGED, WidgetComponentBlueprint.changedArguments(AkytheraScriptTypes.FLOAT),
            ON_COMMITTED, WidgetComponentBlueprint.committedArguments(AkytheraScriptTypes.FLOAT)
        );
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.dragValueBox(this);
    }

    @Override
    public MapCodec<DragValueBoxBlueprint> mapCodec() {
        return CODEC;
    }
}
