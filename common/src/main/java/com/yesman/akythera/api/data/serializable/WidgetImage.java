package com.yesman.akythera.api.data.serializable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// An image a widget draws: `texture` tinted by `color`, or a plain `color` fill while `texture` is null.
/// The widget decides how `texture` is read: a GUI sprite id, or a whole texture.
@ScriptType(autoRegister = false)
public record WidgetImage(@Nullable Identifier texture, RGBA color) {
    public static final Codec<WidgetImage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.optionalFieldOf("texture").forGetter(ParseUtils.provideOptional(WidgetImage::texture)),
        RGBA.INT_LIST_CODEC.fieldOf("color").forGetter(WidgetImage::color)
    ).apply(instance, (texture, color) -> new WidgetImage(texture.orElse(null), color)));

    /// A plain `color` fill.
    public static WidgetImage of(final RGBA color) {
        return new WidgetImage(null, color);
    }

    /// `texture` drawn untinted.
    public static WidgetImage of(final Identifier texture) {
        return new WidgetImage(texture, new RGBA(1.0F, 1.0F, 1.0F, 1.0F));
    }
}
