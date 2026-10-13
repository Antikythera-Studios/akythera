package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Enum interface for [AkytheraEditBox] background styles.
///
/// Mirrors the [ButtonTheme] pattern. Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface EditBoxTheme extends WidgetTheme {
    void renderBackground(AkytheraEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    @FunctionalInterface
    interface EditBoxRenderer {
        void renderBackground(AkytheraEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }
}
