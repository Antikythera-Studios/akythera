package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.ResourceKey;

/// Builds the [AkytheraWidget] another [WidgetBlueprint] of [WidgetUsage#WIDGET] describes, placed by `layout`.
public record WidgetBlueprintComponentBlueprint(WidgetLayout layout, int order, ResourceKey<WidgetBlueprint> widget, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<WidgetBlueprintComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(WidgetBlueprintComponentBlueprint::layout),
                ORDER_CODEC.forGetter(WidgetBlueprintComponentBlueprint::order),
                ResourceKey.codec(AkytheraRegistries.DataPack.WIDGET_KEY).fieldOf("widget").forGetter(WidgetBlueprintComponentBlueprint::widget),
                ENABLED_CODEC.forGetter(WidgetBlueprintComponentBlueprint::enabled)
            ).apply(instance, WidgetBlueprintComponentBlueprint::new)
    );

    /// Unknown while the referenced blueprint is parsed apart from this one. [WidgetBlueprint#VALIDATOR] checks it.
    @Override
    public boolean acceptsInput() {
        return false;
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.reference(this, host);
    }

    @Override
    public MapCodec<WidgetBlueprintComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
