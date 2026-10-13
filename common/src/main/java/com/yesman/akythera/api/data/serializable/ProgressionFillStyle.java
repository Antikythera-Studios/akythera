package com.yesman.akythera.api.data.serializable;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/// How a progression bar's filled and empty images share the bar.
public enum ProgressionFillStyle implements StringRepresentable {
    /// Both images span the whole bar.
    EQUAL,
    /// The empty image spans the whole bar and the filled one is inset by the highlight thickness, so the empty
    /// image rims it.
    HIGHLIGHT_EMPTY,
    /// The filled image spans the whole bar and the empty one is inset by the highlight thickness, so the filled
    /// image rims it.
    HIGHLIGHT_FILLED;

    public static final Codec<ProgressionFillStyle> CODEC = StringRepresentable.fromEnum(ProgressionFillStyle::values);

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
