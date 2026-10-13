package com.yesman.akythera.client.api.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/// A small picture a [ComboBoxTheme] draws in front of a combo label.
public interface ComboIcon {
    /// Width every icon is drawn within.
    int WIDTH = 14;

    /// Height every icon is drawn within.
    int HEIGHT = 8;

    /// Draws the icon inside the [#WIDTH] x [#HEIGHT] box whose top left corner is `(x, y)`.
    void draw(GuiGraphicsExtractor graphics, int x, int y);
}
