package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Enum interface for [CheckBox] styles: the square box and its check mark. The label beside it stays the
/// widget's own.
///
/// Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface CheckBoxTheme extends WidgetTheme {
    /// Draws the box at the widget's top-left corner.
    ///
    /// @param size      edge length of the box, in pixels
    /// @param markColor the check mark's color, as packed ARGB
    void renderBox(CheckBox widget, GuiGraphicsExtractor graphics, int size, int markColor);

    @FunctionalInterface
    interface BoxRenderer {
        void renderBox(CheckBox widget, GuiGraphicsExtractor graphics, int size, int markColor);
    }
}
