package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ButtonTheme extends WidgetTheme {
    void renderBackground(AkytheraButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    int getFontColor(AkytheraButton widget);

    @FunctionalInterface
    interface ButtonRenderer {
        void renderBackground(AkytheraButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }
}
