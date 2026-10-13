package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// [ComboBox] styles. Names are suffixed by '_COMBO_BOX' to avoid crash with the other [WidgetTheme] enums.
public enum BuiltInComboBoxTheme implements ComboBoxTheme {
    /// Black fill, state-colored 1px outline, white label, no icon.
    DEFAULT_COMBO_BOX(
        (widget, g, _, _, _) -> {
            int outlineColor = widget.isMarked()
                ? 0xFFFF5555 : widget.isFocused()
                    ? -1 : widget.isActive()
                        ? -6250336 : -12566463;
            g.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), -16777216);
            g.outline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), outlineColor);
        },
        (g, font, x, y, width, height, text, _, textColor, drawArrow) -> {
            int pad = 4;
            int arrowSlot = drawArrow ? 10 : 0;
            String clipped = font.plainSubstrByWidth(text, Math.max(0, width - pad * 2 - arrowSlot));
            int textY = y + height / 2 - font.lineHeight / 2 + 1;
            g.text(font, Component.literal(clipped), x + pad, textY, textColor, false);
            if (drawArrow) {
                g.text(font, Component.literal("▼"), x + width - 8, textY, textColor, false);
            }
        },
        (font, text, _, drawArrow) -> 8 + font.width(text) + (drawArrow ? 10 : 0)
    );

    private final BackgroundRenderer backgroundRenderer;
    private final TextRenderer textRenderer;
    private final TextMeasurer textMeasurer;
    private final int id;

    BuiltInComboBoxTheme(BackgroundRenderer backgroundRenderer, TextRenderer textRenderer, TextMeasurer textMeasurer) {
        this.backgroundRenderer = backgroundRenderer;
        this.textRenderer = textRenderer;
        this.textMeasurer = textMeasurer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(ComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        backgroundRenderer.render(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public void renderText(
        GuiGraphicsExtractor graphics, Font font,
        int x, int y, int width, int height,
        String text, @Nullable ComboIcon icon, int textColor, boolean drawArrow
    ) {
        textRenderer.render(graphics, font, x, y, width, height, text, icon, textColor, drawArrow);
    }

    @Override
    public int textCellWidth(Font font, String text, @Nullable ComboIcon icon, boolean drawArrow) {
        return textMeasurer.measure(font, text, icon, drawArrow);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
