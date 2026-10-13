package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.ComboBox;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Builds a [ComboBox] picking one of `items`.
///
/// @param maxRows rows the dropdown shows before it scrolls
/// @param theme   the combo box theme's name, or null for the default one
public record ComboBoxBlueprint(
    WidgetLayout layout,
    int order,
    LocalizedMessage title,
    List<LocalizedMessage> items,
    int maxRows,
    boolean searchable,
    boolean clearable,
    @Nullable Identifier theme,
    boolean enabled
) implements WidgetComponentBlueprint {
    public static final MapCodec<ComboBoxBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(ComboBoxBlueprint::layout),
                ORDER_CODEC.forGetter(ComboBoxBlueprint::order),
                LocalizedMessage.CODEC.optionalFieldOf("title", LocalizedMessage.EMPTY).forGetter(ComboBoxBlueprint::title),
                LocalizedMessage.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(ComboBoxBlueprint::items),
                Codec.intRange(1, 64).optionalFieldOf("max_rows", 8).forGetter(ComboBoxBlueprint::maxRows),
                Codec.BOOL.optionalFieldOf("searchable", false).forGetter(ComboBoxBlueprint::searchable),
                Codec.BOOL.optionalFieldOf("clearable", true).forGetter(ComboBoxBlueprint::clearable),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(ComboBoxBlueprint::theme)),
                ENABLED_CODEC.forGetter(ComboBoxBlueprint::enabled)
            ).apply(instance, (layout, order, title, items, maxRows, searchable, clearable, theme, enabled) ->
                new ComboBoxBlueprint(layout, order, title, items, maxRows, searchable, clearable, theme.orElse(null), enabled))
    );

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(ON_CHANGED, WidgetComponentBlueprint.changedArguments(AkytheraScriptTypes.LOCALIZED_MESSAGE));
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.comboBox(this);
    }

    @Override
    public MapCodec<ComboBoxBlueprint> mapCodec() {
        return CODEC;
    }
}
