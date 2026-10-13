package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Builds a [CheckBox] labelled `message`.
///
/// @param theme the check box theme's name, or null for the default one
public record CheckBoxBlueprint(WidgetLayout layout, int order, LocalizedMessage message, @Nullable Identifier theme, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<CheckBoxBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(CheckBoxBlueprint::layout),
                ORDER_CODEC.forGetter(CheckBoxBlueprint::order),
                LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(CheckBoxBlueprint::message),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(CheckBoxBlueprint::theme)),
                ENABLED_CODEC.forGetter(CheckBoxBlueprint::enabled)
            ).apply(instance, (layout, order, message, theme, enabled) -> new CheckBoxBlueprint(layout, order, message, theme.orElse(null), enabled))
    );

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(ON_CHANGED, WidgetComponentBlueprint.changedArguments(AkytheraScriptTypes.BOOLEAN));
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.checkBox(this);
    }

    @Override
    public MapCodec<CheckBoxBlueprint> mapCodec() {
        return CODEC;
    }
}
