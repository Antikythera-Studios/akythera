package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ButtonTheme extends WidgetTheme {
    void renderBackground(AnchoredButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    int getFontColor(AnchoredButton widget);

    @FunctionalInterface
    interface ButtonRenderer {
        void renderBackground(AnchoredButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }
}
