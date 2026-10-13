package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Enum interface for [DragValueBox] styles. Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface DragValueBoxTheme extends WidgetTheme {
    /// Draws the box, its value bar and its value text. Not called while the box takes typed text.
    void render(DragValueBox<?> widget, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float a);

    @FunctionalInterface
    interface DragValueBoxRenderer {
        void render(DragValueBox<?> widget, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float a);
    }
}
