package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Builds an [AkytheraButton]. Without a `theme`, it draws the three state sprites.
///
/// @param theme the button theme's name, or null to draw the sprites
public record ButtonBlueprint(
    WidgetLayout layout,
    int order,
    LocalizedMessage message,
    @Nullable Identifier theme,
    WidgetImage normalImage,
    WidgetImage focusedImage,
    WidgetImage disabledImage,
    @Nullable Holder<SoundEvent> hoverSound,
    @Nullable Holder<SoundEvent> pressSound,
    boolean enabled
) implements WidgetComponentBlueprint {
    /// Vanilla's button sprites, the images a button starts with.
    public static final WidgetImage DEFAULT_NORMAL_IMAGE = WidgetImage.of(Identifier.withDefaultNamespace("widget/button"));
    public static final WidgetImage DEFAULT_FOCUSED_IMAGE = WidgetImage.of(Identifier.withDefaultNamespace("widget/button_highlighted"));
    public static final WidgetImage DEFAULT_DISABLED_IMAGE = WidgetImage.of(Identifier.withDefaultNamespace("widget/button_disabled"));

    public static final MapCodec<ButtonBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                LAYOUT_CODEC.forGetter(ButtonBlueprint::layout),
                ORDER_CODEC.forGetter(ButtonBlueprint::order),
                LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(ButtonBlueprint::message),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(ButtonBlueprint::theme)),
                WidgetImage.CODEC.optionalFieldOf("normal_image", DEFAULT_NORMAL_IMAGE).forGetter(ButtonBlueprint::normalImage),
                WidgetImage.CODEC.optionalFieldOf("focused_image", DEFAULT_FOCUSED_IMAGE).forGetter(ButtonBlueprint::focusedImage),
                WidgetImage.CODEC.optionalFieldOf("disabled_image", DEFAULT_DISABLED_IMAGE).forGetter(ButtonBlueprint::disabledImage),
                SoundEvent.CODEC.optionalFieldOf("hover_sound").forGetter(ParseUtils.provideOptional(ButtonBlueprint::hoverSound)),
                SoundEvent.CODEC.optionalFieldOf("press_sound").forGetter(ParseUtils.provideOptional(ButtonBlueprint::pressSound)),
                ENABLED_CODEC.forGetter(ButtonBlueprint::enabled)
            ).apply(instance, (layout, order, message, theme, normal, focused, disabled, hoverSound, pressSound, enabled) ->
                new ButtonBlueprint(layout, order, message, theme.orElse(null), normal, focused, disabled, hoverSound.orElse(null), pressSound.orElse(null), enabled))
    );

    @Override
    public Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of(ON_PRESSED, List.of());
    }

    @Override
    public PatchComponent instantiate(final ComponentAttachable host) {
        return BlueprintWidgets.button(this);
    }

    @Override
    public MapCodec<ButtonBlueprint> mapCodec() {
        return CODEC;
    }
}
