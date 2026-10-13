package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// An unmodifiable text widget
public class Static extends AbstractWidget implements AnchoredWidget {
    private final Font font;
    private int fontColor = 0xFFFFFFFF;

    public Static(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        String translatable
    ) {
        this(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, Component.translatable(translatable), Component.translatable(translatable + ".tooltip"));
    }

    public Static(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Component message
    ) {
        this(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, message, null);
    }

    public Static(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Component message,
        @Nullable Component tooltip
    ) {
        super(0, 0, 0, 0, message);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;

        setTooltip(tooltip == null ? null : Tooltip.create(tooltip));
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        String correctedString = font.plainSubstrByWidth(getMessage().getString(), getWidth());
        guiGraphics.text(font, correctedString, getX(), getY() + height / 2 - font.lineHeight / 2, fontColor, false);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= (double) getX() && mouseY >= (double) getY() && mouseX < (double) (getX() + textWidth()) && mouseY < (double) (getY() + height);
    }

    /// Width of the whole message in pixels, before clipping.
    public int textWidth() {
        return font.width(getMessage());
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    public void setColor(int r, int g, int b) {
        fontColor = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /// The text color, ARGB.
    public void setColor(int argb) {
        fontColor = argb;
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}