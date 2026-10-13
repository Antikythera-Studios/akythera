package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.Image;

/// Builds an [Image] drawing `image`, its texture read whole.
public record ImageBlueprint(WidgetLayout layout, int order, WidgetImage image, boolean enabled) implements WidgetComponentBlueprint {
    public static final MapCodec<ImageBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(ImageBlueprint::layout),
                ORDER_CODEC.forGetter(ImageBlueprint::order),
                WidgetImage.CODEC.fieldOf("image").forGetter(ImageBlueprint::image),
                ENABLED_CODEC.forGetter(ImageBlueprint::enabled)
            ).apply(instance, ImageBlueprint::new)
    );

    @Override
    public boolean acceptsInput() {
        return false;
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.image(this);
    }

    @Override
    public MapCodec<ImageBlueprint> mapCodec() {
        return CODEC;
    }
}
