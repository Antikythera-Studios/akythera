package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/// Enum interface for [ComboBox] styles.
///
/// Mirrors the [EditBoxTheme] / [ButtonTheme]
/// pattern: a theme owns both the box background and — unlike the other widget themes — the
/// **text render** itself, so a theme can choose whether and how to draw a [ComboIcon] in front of
/// the label. The same [#renderText] is reused for the collapsed display and for each
/// row of the open dropdown, so the two stay visually consistent.
///
/// Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface ComboBoxTheme extends WidgetTheme {
    /// Draws the collapsed combo's box: fill + border, styled by the widget's focus / active /
    /// marked state.
    void renderBackground(ComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    /// Draws a label inside a `[x, y, width, height]` cell: an optional `icon`, the (clipped) `text`,
    /// and — for the collapsed display only (`drawArrow`) — the dropdown arrow on the right edge.
    /// Shared by the collapsed box and the dropdown rows so the icon is embedded identically in both.
    void renderText(
        GuiGraphicsExtractor graphics, Font font,
        int x, int y, int width, int height,
        String text, @Nullable ComboIcon icon, int textColor, boolean drawArrow
    );

    /// Width a [#renderText] cell needs to show `text` unclipped.
    int textCellWidth(Font font, String text, @Nullable ComboIcon icon, boolean drawArrow);

    @FunctionalInterface
    interface BackgroundRenderer {
        void render(ComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }

    @FunctionalInterface
    interface TextRenderer {
        void render(
            GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height,
            String text, @Nullable ComboIcon icon, int textColor, boolean drawArrow
        );
    }

    @FunctionalInterface
    interface TextMeasurer {
        int measure(Font font, String text, @Nullable ComboIcon icon, boolean drawArrow);
    }
}
