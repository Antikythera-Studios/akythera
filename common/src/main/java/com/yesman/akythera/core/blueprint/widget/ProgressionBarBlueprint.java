package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.serializable.ProgressionFillDirection;
import com.yesman.akythera.api.data.serializable.ProgressionFillStyle;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.ProgressionBar;
import java.util.Map;


/// Builds a [ProgressionBar].
///
/// @param flashTime seconds of real time the lerp area holds its full span after a change
/// @param lerpTime  seconds of real time an interpolation takes, see `fixedLerpTime`
public record ProgressionBarBlueprint(
    WidgetLayout layout,
    int order,
    ProgressionFillStyle fillStyle,
    ProgressionFillDirection fillDirection,
    int highlightThickness,
    float flashTime,
    float lerpTime,
    boolean fixedLerpTime,
    WidgetImage filledImage,
    WidgetImage emptyImage,
    WidgetImage lerpImage,
    boolean enabled
) implements WidgetComponentBlueprint {
    /// The binding answering the bar's progression, from 0 to 1.
    public static final String PROGRESSION = "progression";

    /// The images a bar starts with: white fill, a dark empty part, and an amber lerp area.
    public static final WidgetImage DEFAULT_FILLED_IMAGE = WidgetImage.of(new RGBA(1.0F, 1.0F, 1.0F));
    public static final WidgetImage DEFAULT_EMPTY_IMAGE = WidgetImage.of(new RGBA(0.12F, 0.12F, 0.12F));
    public static final WidgetImage DEFAULT_LERP_IMAGE = WidgetImage.of(new RGBA(0.9F, 0.75F, 0.25F));

    public static final MapCodec<ProgressionBarBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(ProgressionBarBlueprint::layout),
                ORDER_CODEC.forGetter(ProgressionBarBlueprint::order),
                ProgressionFillStyle.CODEC.optionalFieldOf("fill_style", ProgressionFillStyle.EQUAL).forGetter(ProgressionBarBlueprint::fillStyle),
                ProgressionFillDirection.CODEC.optionalFieldOf("fill_direction", ProgressionFillDirection.LEFT_TO_RIGHT).forGetter(ProgressionBarBlueprint::fillDirection),
                Codec.intRange(0, 64).optionalFieldOf("highlight_thickness", 1).forGetter(ProgressionBarBlueprint::highlightThickness),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("flash_time", 0.0F).forGetter(ProgressionBarBlueprint::flashTime),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("lerp_time", 0.0F).forGetter(ProgressionBarBlueprint::lerpTime),
                Codec.BOOL.optionalFieldOf("fixed_lerp_time", false).forGetter(ProgressionBarBlueprint::fixedLerpTime),
                WidgetImage.CODEC.optionalFieldOf("filled_image", DEFAULT_FILLED_IMAGE).forGetter(ProgressionBarBlueprint::filledImage),
                WidgetImage.CODEC.optionalFieldOf("empty_image", DEFAULT_EMPTY_IMAGE).forGetter(ProgressionBarBlueprint::emptyImage),
                WidgetImage.CODEC.optionalFieldOf("lerp_image", DEFAULT_LERP_IMAGE).forGetter(ProgressionBarBlueprint::lerpImage),
                ENABLED_CODEC.forGetter(ProgressionBarBlueprint::enabled)
            ).apply(instance, ProgressionBarBlueprint::new)
    );

    @Override
    public boolean acceptsInput() {
        return false;
    }

    @Override
    public Map<String, ScriptType> bindings() {
        return Map.of(PROGRESSION, AkytheraScriptTypes.FLOAT.value());
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.progressionBar(this);
    }

    @Override
    public MapCodec<ProgressionBarBlueprint> mapCodec() {
        return CODEC;
    }
}
