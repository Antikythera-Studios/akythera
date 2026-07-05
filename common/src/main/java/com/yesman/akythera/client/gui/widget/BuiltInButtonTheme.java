package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;

import java.util.function.Function;

import static com.yesman.akythera.client.gui.widget.AnchoredButton.*;
import static net.minecraft.client.gui.components.AbstractButton.SPRITES;

public enum BuiltInButtonTheme implements ButtonTheme {
    VANILLA_BUTTON(
        (AnchoredButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
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
        (AnchoredButton widget) -> 0xFFFFFFFF
    ),
    CONFIG_BUTTON(
        (AnchoredButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            int color = (widget.isHighlighted() ? 0xFF2A2A2A : widget.active ? 0x000A0A0A : 0x00000000)
                | (int)(widget.getAlpha() * 255) << 24; // apply widget alpha
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        (AnchoredButton widget) -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),
    NUMBERED_TAB_BUTTON(
        (AnchoredButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                NUMBERED_TAB_SPRITES.get(widget.active, widget.isHighlighted()),
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                ARGB.white(widget.getAlpha())
            );
        },
        (AnchoredButton widget) -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),
    UPSIDE_EMOTE_WHEEL_BUTTON(
        (AnchoredButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                EMOTE_WHEEL_UP_SPRITES.get(widget.isActive(), widget.isHighlighted()),
                widget.getX() - 9,
                widget.getY() + 1,
                61,
                54,
                ARGB.white(widget.getAlpha())
            );
        },
        (AnchoredButton widget) -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),
    DOWNSIDE_EMOTE_WHEEL_BUTTON(
        (AnchoredButton widget, final GuiGraphicsExtractor graphics, int _, int _, float _) -> {
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                EMOTE_WHEEL_DOWN_SPRITES.get(widget.isActive(), widget.isHighlighted()),
                widget.getX() - 9,
                widget.getY() + 1,
                61,
                54,
                ARGB.white(widget.getAlpha())
            );
        },
        (AnchoredButton widget) -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),
    BACKGROUNDLESS_BUTTON(
        (AnchoredButton _, GuiGraphicsExtractor _, int _, int _, float _) -> {
        },
        (AnchoredButton widget) -> 0xFFFFFFFF
    );

    final ButtonRenderer buttonRenderer;
    final Function<AnchoredButton, Integer> fontColorProvider;
    final int id;

    BuiltInButtonTheme(ButtonRenderer buttonRenderer, Function<AnchoredButton, Integer> fontColorProvider) {
        this.buttonRenderer = buttonRenderer;
        this.fontColorProvider = fontColorProvider;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AnchoredButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.buttonRenderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int getFontColor(AnchoredButton widget) {
        return fontColorProvider.apply(widget);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
