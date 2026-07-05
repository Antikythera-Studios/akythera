package com.yesman.akythera.client.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.client.gui.widget.common.PressableWidget;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class CheckBox extends AbstractWidget implements AnchoredWidget, DataBoundWidget<Boolean>, PressableWidget {
    private final Font font;
    private final boolean showMessage;
    private boolean checked;

    public CheckBox(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        final AnchoredWidget.VerticalAnchorType verticalAnchor,
        final Callable<Boolean> dataProvider,
        final Consumer<Boolean> onWidgetChanged,
        final Component message,
        boolean showMessage
    ) {
        super(0, 0, 0, 0, message);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;
        this.showMessage = showMessage;
    }

    /// Copied from [AbstractWidget#mouseClicked], without playing clicking sound
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) {
            if (isValidClickButton(event.buttonInfo())) {
                boolean isMouseOver = isMouseOverCheckBox(event.x(), event.y());

                if (isMouseOver) {
                    // No play sound
                    onPressed();
                    return true;
                }
            }
        }

        return false;
    }

    /// Copied from [AbstractButton#keyPressed], without playing clicking sound
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (active && visible) {
            if (event.isSelection()) {
                onPressed();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /// Custom implementation of [#isMouseOver], but restricted to check box part
    public boolean isMouseOverCheckBox(double x, double y) {
        int rectangleLength = Math.min(getWidth(), getHeight());
        return active && visible && x >= (double)getX() && y >= (double)getY() && x < (double)(getX() + rectangleLength) && y < (double)(getY() + rectangleLength);
    }

    @Override
    public void onPressed() {
        checked = !checked;
        onWidgetChanged.accept(checked);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int rectangleLength = Math.min(getWidth(), getHeight());
        int outlineColor = isFocused() ? -1 : isActive() ? -6250336 : -12566463;

        guiGraphics.fill(getX(), getY(), getX() + rectangleLength, getY() + rectangleLength, outlineColor);
        guiGraphics.fill(getX() + 1, getY() + 1, getX() + rectangleLength - 1, getY() + rectangleLength - 1, -16777216);

        if (checked) {
            guiGraphics.fill(getX() + 2, getY() + 2, getX() + rectangleLength - 2, getY() + rectangleLength - 2, -1);
        }

        int fontColor = isActive() ? 16777215 : 4210752;

        if (showMessage) {
            guiGraphics.text(font, getMessage(), getX() + rectangleLength + 4, getY() + height / 2 - font.lineHeight / 2 + 1, fontColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    /// Returns the state whether the widget is checked, not a bound data value
    public boolean getWidgetValue() {
        return checked;
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

    /// *****************************************************************
    /// [DataBoundWidget] implementations                               *
    /// *****************************************************************
    private final Callable<Boolean> dataProvider;
    private final Consumer<Boolean> onWidgetChanged;

    @Override
    public Callable<Boolean> getDataProvider() {
        return dataProvider;
    }

    @Override
    public Consumer<Boolean> valueChangeCallback() {
        return onWidgetChanged;
    }

    @Override
    public Consumer<Boolean> valueSetter() {
        return (bool) -> checked = bool;
    }

    @Override
    public void reset() {
        checked = false;
    }
}