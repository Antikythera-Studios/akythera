package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.util.helper.MathUtils;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import com.yesman.akythera.client.gui.widget.common.ScrollableWidget;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class TextBox extends AbstractWidget implements AnchoredWidget, ScrollableWidget {
    private static final int LINE_HEIGHT = 18;

    private final Font font;
    private int scrollPosition;
    private int lines;

    public TextBox(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Component message
    ) {
        super(0, 0, 0, 0, message);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
    }

    @Override
    public int getMaxWidgetHeight() {
        return lines * getScrollStride();
    }

    @Override
    public int getScrollStride() {
        return LINE_HEIGHT;
    }

    @Override
    public int getScrollPosition() {
        return scrollPosition;
    }

    @Override
    public void setScrollPosition(int position) {
        scrollPosition = position;
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        lines = font.split(getMessage(), width - 6).size();
    }

    @Override
    public void setMessage(@NonNull Component message) {
        super.setMessage(message);
        lines = font.split(getMessage(), width - 6).size();
        scrollPosition = 0;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xBF000000);

        var textLine = font.split(getMessage(), width - 6 - (isScrollVisible() ? getScrollWidth() : 0));
        int textY = getY() + 6;

        preRenderProcess(guiGraphics);

        for (var text : textLine) {
            guiGraphics.text(font, text, getX() + 3, textY, -1);
            textY += getScrollStride();
        }

        postRenderProcess(guiGraphics);

        computeScrollPositionAndRender(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderScroll(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int minX, int minY, int maxX, int maxY) {
        guiGraphics.fill(minX, minY, maxX, maxY, 0x8BFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean scrollVisible = isScrollVisible();
        if (scrollVisible) moveScroll(-MathUtils.getSign(scrollY));

        return scrollVisible;
    }

    public @Nullable ComponentPath nextFocusPath(@NonNull FocusNavigationEvent focusNavigationEvent) {
        return null;
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
}
