package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// [CheckBox] styles. Names are suffixed by '_CHECK_BOX' to avoid crash with the other [WidgetTheme] enums.
public enum BuiltInCheckBoxTheme implements CheckBoxTheme {
    /// A 1-pixel outlined black square, filled with the mark color when checked. The outline is white when
    /// focused, grey when active, dark grey otherwise.
    DEFAULT_CHECK_BOX(
        (widget, graphics, size, markColor) -> {
            int x = widget.getX();
            int y = widget.getY();
            int outlineColor = widget.isFocused() ? -1 : widget.isActive() ? -6250336 : -12566463;

            graphics.fill(x, y, x + size, y + size, outlineColor);
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, -16777216);

            if (widget.getWidgetValue()) {
                graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, markColor);
            }
        }
    );

    private final BoxRenderer renderer;
    private final int id;

    BuiltInCheckBoxTheme(BoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBox(CheckBox widget, GuiGraphicsExtractor graphics, int size, int markColor) {
        renderer.renderBox(widget, graphics, size, markColor);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
