package com.yesman.akythera.api.data.serializable;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/// The way a progression bar's filled image grows.
public enum ProgressionFillDirection implements StringRepresentable {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    /// A sector of the ellipse inscribed in the bar, growing clockwise from the top.
    RADIAL_CIRCLE_CLOCKWISE,
    RADIAL_CIRCLE_COUNTER_CLOCKWISE,
    /// A sector of the bar's own rect, growing clockwise from the top.
    RADIAL_BOX_CLOCKWISE,
    RADIAL_BOX_COUNTER_CLOCKWISE;

    public static final Codec<ProgressionFillDirection> CODEC = StringRepresentable.fromEnum(ProgressionFillDirection::values);

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
