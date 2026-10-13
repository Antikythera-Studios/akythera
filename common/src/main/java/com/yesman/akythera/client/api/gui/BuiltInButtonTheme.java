package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;

import java.util.function.Function;

import static com.yesman.akythera.client.api.gui.AkytheraButton.*;
import static net.minecraft.client.gui.components.AbstractButton.SPRITES;

public enum BuiltInButtonTheme implements ButtonTheme {
    VANILLA_BUTTON(
        (AkytheraButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SPRITES.get(widget.active, widget.isHighlighted()),
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                ARGB.white(widget.getAlpha())
            );
        },
        (AkytheraButton widget) -> 0xFFFFFFFF
    ),
    CONFIG_BUTTON(
        (AkytheraButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            int color = (widget.isHighlighted() ? 0xFF2A2A2A : widget.active ? 0x000A0A0A : 0x00000000)
                | (int)(widget.getAlpha() * 255) << 24; // apply widget alpha
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        (AkytheraButton widget) -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),
    BACKGROUNDLESS_BUTTON(
        (AkytheraButton _, GuiGraphicsExtractor _, int _, int _, float _) -> {
        },
        (AkytheraButton widget) -> 0xFFFFFFFF
    );

    final ButtonRenderer buttonRenderer;
    final Function<AkytheraButton, Integer> fontColorProvider;
    final int id;

    BuiltInButtonTheme(ButtonRenderer buttonRenderer, Function<AkytheraButton, Integer> fontColorProvider) {
        this.buttonRenderer = buttonRenderer;
        this.fontColorProvider = fontColorProvider;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AkytheraButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.buttonRenderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int getFontColor(AkytheraButton widget) {
        return fontColorProvider.apply(widget);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
