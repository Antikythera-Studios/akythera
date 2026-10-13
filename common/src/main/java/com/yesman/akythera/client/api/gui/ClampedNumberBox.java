package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.PlaceholderExpressionWidget;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClampedNumberBox<T extends Number> extends AbstractContainerWidget implements AnchoredWidget, PlaceholderExpressionWidget<T> {
    private static final int PADDING = 2;
    private static final int ELEMENTS_HEIGHT = 15;

    private final T minValue;
    private final T maxValue;
    private final T increment;
    private final T decrement;

    private final Function<String, T> textParser;
    private final Function<T, String> toString;
    private final Comparator<T> comparator;
    private final BiFunction<T, T, T> sum;

    @Nullable
    private final AkytheraButton increaseButton;
    @Nullable
    private final AkytheraButton decreaseButton;
    private final EditBox editBox;

    @Nullable
    private T preEditValue;
    @Nullable
    private BiConsumer<T, T> onCommitted;

    /// Runs when the text field takes keyboard focus.
    @Nullable
    private Runnable onFocused;

    /// Drawn as the [EditBox] hint — grey, and only while the text is empty and unfocused — while the
    /// bound value is unset. Null = no placeholder.
    @Nullable
    private T placeholderValue;

    private int focusIndex;

    public ClampedNumberBox(
        Font font,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor, VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider, Consumer<T> onWidgetChanged, Component message,
        boolean makeButtons,
        T minValue, T maxValue, T increment, T decrement,
        Function<String, T> valueParser,
        Function<T, String > toString,
        Comparator<T> comparator,
        BiFunction<T, T, T> sum
    ) {
        super(0, 0, 0, 0, message, AbstractScrollArea.defaultSettings(0)); // Scroll bar unused since increment and decrement buttons are on same line with edit box widget

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        this.decrement = decrement;
        this.textParser = valueParser;
        this.toString = toString;
        this.comparator = comparator;
        this.sum = sum;

        editBox = new EditBox(font, 0, ELEMENTS_HEIGHT, message) {
            @Override
            public void setFocused(boolean focused) {
                boolean was = isFocused();
                super.setFocused(focused);

                if (focused && !was) {
                    try {
                        preEditValue = dataProvider.call();
                    } catch (Exception ignored) {
                        preEditValue = null;
                    }

                    if (onFocused != null) onFocused.run();
                }

                if (!focused && was) {
                    reformatDisplay();
                    fireCommitIfChanged();
                }
            }
        };
        editBox.setResponder(this::responder);

        if (makeButtons) {
            increaseButton =
                AkytheraButton
                    .buttonBuilder(font, LocalizedMessage.handwritten("+"), () -> increase())
                    .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ELEMENTS_HEIGHT))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ELEMENTS_HEIGHT))
                    .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
                    .verticalAnchorType(VerticalAnchorType.CENTER_HEIGHT)
                    .theme(BuiltInButtonTheme.BACKGROUNDLESS_BUTTON)
                    .isHighlighted(AbstractWidget::isHoveredOrFocused)
                    .build();

            decreaseButton =
                AkytheraButton
                    .buttonBuilder(font, LocalizedMessage.handwritten("-"), () -> decrease())
                    .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ELEMENTS_HEIGHT))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ELEMENTS_HEIGHT))
                    .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
                    .verticalAnchorType(VerticalAnchorType.CENTER_HEIGHT)
                    .theme(BuiltInButtonTheme.BACKGROUNDLESS_BUTTON)
                    .isHighlighted(AbstractWidget::isHoveredOrFocused)
                    .build();
        } else {
            increaseButton = null;
            decreaseButton = null;
        }
    }

    private void responder(String text) {
        try {
            T val = textParser.apply(text);

            if (comparator.compare(val, minValue) < 0) {
                val = minValue;
            } else if (comparator.compare(val, maxValue) > 0) {
                val = maxValue;
            }

            valueChangeCallback().accept(val);
        } catch (NumberFormatException ignored) {
        }
    }

    private void reformatDisplay() {
        try {
            T val = textParser.apply(editBox.getValue());

            if (comparator.compare(val, minValue) < 0) {
                val = minValue;
            } else if (comparator.compare(val, maxValue) > 0) {
                val = maxValue;
            }

            String str = toString.apply(val);

            if (str.isEmpty() || ParseUtils.isParsable(str, textParser)) {
                editBox.setResponder(null);
                editBox.setValue(str);
                editBox.setResponder(this::responder);
                valueChangeCallback().accept(val);
            }
        } catch (NumberFormatException ignored) {
            try {
                T pulled = dataProvider.call();
                editBox.setResponder(null);
                editBox.setValue(toString.apply(pulled));
                editBox.setResponder(this::responder);
            } catch (Exception ignored2) {
            }
        }
    }

    private T getAsValue() throws NumberFormatException {
        return textParser.apply(editBox.getValue());
    }

    /// Pushes a bound value in. Null is the unset state, where the box empties and
    /// [#placeholderValue] takes over.
    public void setValue(@Nullable T value) {
        if (value == null) {
            editBox.setValue("");
            return;
        }

        String text = toString.apply(value);

        if (ParseUtils.isParsable(text, textParser)) {
            T val = textParser.apply(text);

            if (comparator.compare(val, minValue) < 0) {
                val = minValue;
            } else if (comparator.compare(val, maxValue) > 0) {
                val = maxValue;
            }

            String str = toString.apply(val);

            if (str.isEmpty() || ParseUtils.isParsable(str, textParser)) {
                editBox.setValue(toString.apply(val));
                editBox.moveCursorToEnd(true);
                valueChangeCallback().accept(val);
            }
        } else {
            if (text.isEmpty() || ParseUtils.isParsable(text, textParser)) {
                editBox.setValue(text);
                editBox.moveCursorToEnd(true);
            }
        }
    }

    /// Turns the text field read-only. The value still refreshes through [#setValue].
    public void setEditable(boolean editable) {
        editBox.setEditable(editable);
    }

    public void increase() {
        fireDiscreteCommit(increment);
    }

    public void decrease() {
        fireDiscreteCommit(decrement);
    }

    private void fireDiscreteCommit(T delta) {
        try {
            T before = dataProvider.call();
            setValue(sum.apply(getAsValue(), delta));

            if (onCommitted == null) return;
            T after = dataProvider.call();

            if (!Objects.equals(before, after)) {
                onCommitted.accept(before, after);
            }
        } catch (Exception ignored) {
        }
    }

    public void setOnCommitted(@Nullable BiConsumer<T, T> cb) {
        this.onCommitted = cb;
    }

    public void setOnFocused(@Nullable Runnable onFocused) {
        this.onFocused = onFocused;
    }

    private void fireCommitIfChanged() {
        if (onCommitted == null || preEditValue == null) {
            preEditValue = null;
            return;
        }

        try {
            T current = dataProvider.call();

            if (!Objects.equals(preEditValue, current)) {
                onCommitted.accept(preEditValue, current);
            }
        } catch (Exception _) {
        } finally {
            preEditValue = null;
        }
    }

    @Override
    protected int contentHeight() {
        return editBox.getHeight();
    }

    @Override
    public void setX(int x) {
        super.setX(x);

        if (increaseButton != null) increaseButton.setX(x);
        editBox.setX(x);
        if (decreaseButton != null) decreaseButton.setX(x);
    }

    @Override
    public void setY(int y) {
        super.setY(y);

        if (increaseButton != null) increaseButton.setY(y);
        editBox.setY(y);
        if (decreaseButton != null) decreaseButton.setY(y);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (increaseButton != null) increaseButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        editBox.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (decreaseButton != null) decreaseButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        if (increaseButton != null && decreaseButton != null) {
            return List.of(increaseButton, editBox, decreaseButton);
        } else {
            return List.of(editBox);
        }
    }

    public @Nullable ComponentPath nextFocusPath(@NonNull FocusNavigationEvent focusNavigationEvent) {
        if (focusNavigationEvent instanceof FocusNavigationEvent.InitialFocus) {
            focusIndex = 0;
            return ComponentPath.path(this, ComponentPath.leaf(children().getFirst()));
        }

        if (isFocused()) {
            if (focusNavigationEvent instanceof FocusNavigationEvent.ArrowNavigation(
                ScreenDirection direction, ScreenRectangle _
            )) {
                switch (direction) {
                    case LEFT -> {
                        return focusIndex > 0 ? ComponentPath.path(this, ComponentPath.leaf(children().get(--focusIndex))) : null;
                    }
                    case RIGHT -> {
                        return focusIndex < children().size() - 1 ? ComponentPath.path(this, ComponentPath.leaf(children().get(++focusIndex))) : null;
                    }
                }

                return null;
            } else if (focusNavigationEvent instanceof FocusNavigationEvent.TabNavigation(boolean forward)) {
                if (forward) {
                    return focusIndex < children().size() - 1 ? ComponentPath.path(this, ComponentPath.leaf(children().get(++focusIndex))) : null;
                } else {
                    return focusIndex > 0 ? ComponentPath.path(this, ComponentPath.leaf(children().get(--focusIndex))) : null;
                }
            }
        } else {
            if (focusNavigationEvent instanceof FocusNavigationEvent.ArrowNavigation(
                ScreenDirection direction, ScreenRectangle _
            )) {
                focusIndex = switch (direction) {
                    case UP, DOWN -> focusIndex;
                    case LEFT -> children().size() - 1;
                    case RIGHT -> 0;
                };

                return ComponentPath.path(this, ComponentPath.leaf(children().get(focusIndex)));
            } else if (focusNavigationEvent instanceof FocusNavigationEvent.TabNavigation(boolean forward)) {
                if (forward) {
                    focusIndex = 0;
                } else {
                    focusIndex = children().size() - 1;
                }

                return ComponentPath.path(this, ComponentPath.leaf(children().get(focusIndex)));
            }
        }

        return null;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        focusIndex = 0;

        for (GuiEventListener guieventlistener : children()) {
            if (guieventlistener.mouseClicked(event, doubleClick)) {
                setFocused(guieventlistener);

                if (event.button() == 0) {
                    setDragging(true);
                }

                return true;
            }

            focusIndex++;
        }

        focusIndex = 0;

        return false;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double scrollX, final double scrollY) {
        // Nothing to scroll. Skip.
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);

        // Reformat + commit are handled by the inner EditBox's own setFocused override.
        if (getFocused() != null && !focused) {
            getFocused().setFocused(false);
            setFocused(null);
        }
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && getFocused() == editBox) {
            editBox.setFocused(false);
            
            return true;
        }
        
        return super.keyPressed(event);
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

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        int x1 = increaseButton == null ? getX() : increaseButton.getX() + increaseButton.getWidth() + PADDING;
        int x2 = decreaseButton == null ? getX() + getWidth() : decreaseButton.getX() - PADDING;

        editBox.setPosition(x1, getY());
        editBox.setWidth(x2 - x1);
        editBox.setHeight(getHeight());
        editBox.moveCursorToStart(false);
    }

    /// ***************************************************************
    /// [PlaceholderExpressionWidget] implementations                 *
    /// ***************************************************************
    @Override
    public @Nullable T getPlaceholderValue() {
        return placeholderValue;
    }

    /// [EditBox#setHint] reads the component's style and takes no null, so an empty component is
    /// what clears the hint.
    @Override
    public void setPlaceholderValue(@Nullable T value) {
        this.placeholderValue = value;
        editBox.setHint(value == null ? Component.empty() : Component.literal(toString.apply(value)));
    }

    /// Empty text is the unset state here — the box keeps no value of its own. [EditBox] adds the
    /// focus condition itself before the hint reaches the screen.
    @Override
    public boolean hasPlaceholderValue() {
        return placeholderValue != null && editBox.getValue().isEmpty();
    }


    /*******************************************************************
     * {@link DataBoundWidget} implementations                         *
     *******************************************************************/
    private final Callable<T> dataProvider;
    private final Consumer<T> onWidgetChanged;

    @Override
    public @NonNull Callable<T> getDataProvider() {
        return dataProvider;
    }

    @Override
    public @NonNull Consumer<T> valueChangeCallback() {
        return onWidgetChanged;
    }

    @Override
    public @NonNull Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        editBox.setValue("");
    }
}
