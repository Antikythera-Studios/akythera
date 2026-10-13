package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// [DragValueBox] styles. Names are suffixed by '_DRAG_VALUE_BOX' to avoid crash with the other
/// [WidgetTheme] enums.
public enum BuiltInDragValueBoxTheme implements DragValueBoxTheme {
    /// Dark rounded box with the value on the left, over a bar showing where the value sits in its
    /// range. The bar is grey, and blue while dragged.
    DEFAULT_DRAG_VALUE_BOX(
        (widget, graphics, font, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean scrubbing = widget.isScrubbing();
            int border = scrubbing
                ? 0xFF0766C4 : widget.isActive() && widget.isHovered()
                    ? 0xFF4A4A4A : 0xFF373737;

            // Rounded corners: two crossed fills leave the corner pixels out.
            graphics.fill(x + 1, y, x + w - 1, y + h, border);
            graphics.fill(x, y + 1, x + w, y + h - 1, border);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0F0F0F);

            // Bar inset 2 px, leaving a dark gap inside the border.
            int barRight = x + 2 + Math.round((w - 4) * widget.getRatio());
            graphics.fill(x + 2, y + 2, barRight, y + h - 2, scrubbing ? 0xFF0070E0 : 0xFF383838);

            int textColor = !widget.isActive()
                ? 0xFF7A7A7A : scrubbing
                    ? 0xFFFFFFFF : 0xFFE0E0E0;
            String clipped = font.plainSubstrByWidth(widget.getText(), Math.max(0, w - 8));

            graphics.text(font, clipped, x + 4, y + (h - 8) / 2, textColor, false);
        }
    );

    private final DragValueBoxRenderer renderer;
    private final int id;

    BuiltInDragValueBoxTheme(DragValueBoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void render(DragValueBox<?> widget, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float a) {
        renderer.render(widget, graphics, font, mouseX, mouseY, a);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
