package com.yesman.akythera.api.data.serializable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.util.Mth;

import java.util.List;

/// RGBA color utility for global use
@ScriptType(autoRegister = false)
public record RGBA(float r, float g, float b, float a) {
    public static final Codec<RGBA> INT_LIST_CODEC =
        Codec.intRange(0, 255).listOf().comapFlatMap(
            list -> {
                if (list.size() < 3 || list.size() > 4) return DataResult.error(() -> "RGBA parsing error: expected [r, g, b] or [r, g, b, a]");

                return DataResult.success(new RGBA(
                    list.get(0),
                    list.get(1),
                    list.get(2),
                    list.size() == 3 ? 255 : list.get(3)
                ));
            },
            rgba ->
                List.of(
                    Math.round(rgba.r() * 255.0F),
                    Math.round(rgba.g() * 255.0F),
                    Math.round(rgba.b() * 255.0F),
                    Math.round(rgba.a() * 255.0F)
                )
        );

    public static final Codec<RGBA> FLOAT_LIST_CODEC =
        Codec.floatRange(0.0F, 1.0F).listOf().comapFlatMap(
            list -> {
                if (list.size() < 3 || list.size() > 4) return DataResult.error(() -> "RGBA parsing error: expected [r, g, b] or [r, g, b, a]");

                return DataResult.success(new RGBA(
                    list.get(0),
                    list.get(1),
                    list.get(2),
                    list.size() == 3 ? 1.0F : list.get(3)
                ));
            },
            rgba ->
                List.of(
                    rgba.r(),
                    rgba.g(),
                    rgba.b(),
                    rgba.a()
                )
        );

    /// Opaque RGB
    public RGBA(float r, float g, float b) {
        this(r, g, b, 1.0F);
    }

    /// RGBA from integers (0 - 255)
    public RGBA(int r, int g, int b, int a) {
        this(Mth.clamp(r, 0, 255) / 255.0F, Mth.clamp(g, 0, 255) / 255.0F, Mth.clamp(b, 0, 255) / 255.0F, Mth.clamp(a, 0, 255) / 255.0F);
    }

    /// Opaque RGB from integers (0 - 255)
    public RGBA(int r, int g, int b) {
        this(r, g, b, 255);
    }

    /// The color a [#packARGB] value packs.
    public static RGBA fromARGB(final int argb) {
        return new RGBA((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /// Returns a compact ARGB (Minecraft's standard) expressed in one integer value
    ///
    /// 1 byte represents element degree, from the leftmost, A, R, G, B
    ///
    /// e.g.
    /// ```
    /// R = 15      == 0F (Hexadecimal)
    /// G = 172     == AC
    /// B = 68      == 44
    /// A = 255     == FF
    ///
    /// int packedColor = 0xFF0FAC44
    /// ```
    public int packARGB() {
        // apply shift operations
        int intA = Math.round(a() * 255.0F) << 24;
        int intR = Math.round(r() * 255.0F) << 16;
        int intG = Math.round(g() * 255.0F) << 8;
        int intB = Math.round(b() * 255.0F);

        // merge bits
        return intA | intR | intG | intB;
    }
}
