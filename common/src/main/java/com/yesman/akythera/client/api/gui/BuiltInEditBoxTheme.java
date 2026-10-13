package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/// [AkytheraEditBox] styles. Names are suffixed by '_EDIT_BOX' to avoid crash with the other [WidgetTheme] enums.
public enum BuiltInEditBoxTheme implements EditBoxTheme {
    /// Vanilla [EditBox] style: 1px border (white when focused, gray otherwise), black fill. Border color
    /// priority: warned (red) > focused > editable.
    DEFAULT_EDIT_BOX(
        (widget, graphics, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean focused = widget.isFocused();
            int borderColor = widget.isWarned()
                ? 0xFFAA2222
                : focused
                    ? 0xFFFFFFFF
                    : widget.isEditable()
                        ? 0xFFA0A0A0
                        : 0xFF6F6F6F;
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
            graphics.outline(x, y, w, h, borderColor);
        }
    );

    private final EditBoxRenderer renderer;
    private final int id;

    BuiltInEditBoxTheme(EditBoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AkytheraEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        renderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
