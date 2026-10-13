package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.PlaceholderExpressionWidget;
import com.yesman.akythera.client.api.gui.common.PressableWidget;
import com.yesman.akythera.client.api.gui.common.ThemeApplicableWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

@ScriptType
public class CheckBox extends AbstractWidget
    implements AnchoredWidget, PlaceholderExpressionWidget<Boolean>, PressableWidget, ThemeApplicableWidget<CheckBoxTheme>
{
    /// Tint of the mark and the label while the box draws [#placeholderValue].
    private static final int PLACEHOLDER_COLOR = 0xFF888888;

    private final Font font;
    private final boolean showMessage;

    /// The bound value, or null while it is unset — where [#placeholderValue] takes over. `false` is
    /// a value of its own, so the unset state needs a slot of its own.
    @Nullable
    private Boolean value;

    /// Drawn greyed while [#value] is unset. Null = no placeholder.
    @Nullable
    private Boolean placeholderValue;

    /// Tint of the checked mark and the label, as packed ARGB.
    private int color = 0xFFFFFFFF;

    private CheckBoxTheme theme = BuiltInCheckBoxTheme.DEFAULT_CHECK_BOX;

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
        // A placeholder box carries no value of its own: the press claims one, starting from the
        // opposite of what was drawn.
        value = !getWidgetValue();
        onWidgetChanged.accept(value);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int rectangleLength = Math.min(getWidth(), getHeight());
        int markColor = hasPlaceholderValue() ? PLACEHOLDER_COLOR : color;

        theme.renderBox(this, guiGraphics, rectangleLength, markColor);

        int fontColor = isActive() ? markColor : 0xFF404040;

        if (showMessage) {
            guiGraphics.text(font, getMessage(), getX() + rectangleLength + 4, getY() + height / 2 - font.lineHeight / 2 + 1, fontColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    /// Tints the checked mark and the label. Takes packed ARGB, unlike [Static#setColor].
    public void setColor(int color) {
        this.color = color;
    }

    /// Returns the state whether the widget is checked, not a bound data value
    @ScriptFunction(pure = true)
    public boolean getWidgetValue() {
        return Boolean.TRUE.equals(hasPlaceholderValue() ? placeholderValue : value);
    }

    /// Checks or unchecks the box without calling the change callback.
    @ScriptFunction
    public void setWidgetValue(final boolean value) {
        this.value = value;
    }

    /// ***************************************************************
    /// [PlaceholderExpressionWidget] implementations                 *
    /// ***************************************************************
    @Override
    public @Nullable Boolean getPlaceholderValue() {
        return placeholderValue;
    }

    @Override
    public void setPlaceholderValue(@Nullable Boolean value) {
        this.placeholderValue = value;
    }

    @Override
    public boolean hasPlaceholderValue() {
        return value == null && placeholderValue != null;
    }

    /// ***************************************************************
    /// [ThemeApplicableWidget] implementations                       *
    /// ***************************************************************
    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return theme instanceof CheckBoxTheme;
    }

    @Override
    public @NonNull CheckBoxTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(CheckBoxTheme theme) {
        this.theme = theme;
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
        return bool -> value = bool;
    }

    @Override
    public void reset() {
        value = false;
    }
}