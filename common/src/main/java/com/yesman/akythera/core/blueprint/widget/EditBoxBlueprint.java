package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Builds an [AkytheraEditBox] for text, showing `placeholder` while it is empty.
///
/// @param theme the edit box theme's name, or null for the default one
public record EditBoxBlueprint(WidgetLayout layout, int order, LocalizedMessage placeholder, @Nullable Identifier theme, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<EditBoxBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(EditBoxBlueprint::layout),
                ORDER_CODEC.forGetter(EditBoxBlueprint::order),
                LocalizedMessage.CODEC.optionalFieldOf("placeholder", LocalizedMessage.EMPTY).forGetter(EditBoxBlueprint::placeholder),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(EditBoxBlueprint::theme)),
                ENABLED_CODEC.forGetter(EditBoxBlueprint::enabled)
            ).apply(instance, (layout, order, placeholder, theme, enabled) -> new EditBoxBlueprint(layout, order, placeholder, theme.orElse(null), enabled))
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
        return BlueprintWidgets.editBox(this);
    }

    @Override
    public MapCodec<EditBoxBlueprint> mapCodec() {
        return CODEC;
    }
}
