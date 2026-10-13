package com.yesman.akythera.core.blueprint.widget;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/// Where a widget sits in its parent: the four units and the two anchor types of an [AnchoredWidget], as data a
/// dedicated server reads without the client's anchoring code.
public record WidgetLayout(Unit x1, Unit x2, Unit y1, Unit y2, Horizontal horizontalAnchor, Vertical verticalAnchor) {
    public static final Codec<WidgetLayout> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Unit.CODEC.optionalFieldOf("x1", Unit.ZERO).forGetter(WidgetLayout::x1),
                Unit.CODEC.optionalFieldOf("x2", Unit.ZERO).forGetter(WidgetLayout::x2),
                Unit.CODEC.optionalFieldOf("y1", Unit.ZERO).forGetter(WidgetLayout::y1),
                Unit.CODEC.optionalFieldOf("y2", Unit.ZERO).forGetter(WidgetLayout::y2),
                Horizontal.CODEC.optionalFieldOf("horizontal_anchor", Horizontal.LEFT_WIDTH).forGetter(WidgetLayout::horizontalAnchor),
                Vertical.CODEC.optionalFieldOf("vertical_anchor", Vertical.TOP_HEIGHT).forGetter(WidgetLayout::verticalAnchor)
            ).apply(instance, WidgetLayout::new)
    );

    /// Fills the parent.
    public static final WidgetLayout FILL = new WidgetLayout(Unit.ZERO, Unit.ZERO, Unit.ZERO, Unit.ZERO, Horizontal.LEFT_RIGHT, Vertical.TOP_BOTTOM);

    /// A length: pixels, or a percentage of the parent's length on the same axis.
    public record Unit(int value, boolean fraction) {
        public static final Unit ZERO = new Unit(0, false);

        /// A number reads as pixels, a string ending in `%` as a percentage: `24`, `"50%"`.
        public static final Codec<Unit> CODEC = Codec.either(Codec.INT, Codec.STRING).comapFlatMap(
            either -> either.map(pixels -> DataResult.success(new Unit(pixels, false)), Unit::parse),
            unit -> unit.fraction ? Either.right(unit.value + "%") : Either.left(unit.value)
        );

        private static DataResult<Unit> parse(final String text) {
            String trimmed = text.trim();
            boolean fraction = trimmed.endsWith("%");
            String number = fraction ? trimmed.substring(0, trimmed.length() - 1) : trimmed.endsWith("px") ? trimmed.substring(0, trimmed.length() - 2) : trimmed;

            try {
                return DataResult.success(new Unit(Integer.parseInt(number.trim()), fraction));
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Not a widget unit: " + text + " (expected pixels or a percentage like \"50%\")");
            }
        }
    }

    /// The horizontal anchor types of [AnchoredWidget.HorizontalAnchorType], by the same names.
    public enum Horizontal implements StringRepresentable {
        LEFT_WIDTH, CENTER_WIDTH, RIGHT_WIDTH, LEFT_RIGHT;

        public static final Codec<Horizontal> CODEC = StringRepresentable.fromEnum(Horizontal::values);

        @Override
        public @NonNull String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// The vertical anchor types of [AnchoredWidget.VerticalAnchorType], by the same names.
    public enum Vertical implements StringRepresentable {
        TOP_HEIGHT, CENTER_HEIGHT, BOTTOM_HEIGHT, TOP_BOTTOM;

        public static final Codec<Vertical> CODEC = StringRepresentable.fromEnum(Vertical::values);

        @Override
        public @NonNull String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
