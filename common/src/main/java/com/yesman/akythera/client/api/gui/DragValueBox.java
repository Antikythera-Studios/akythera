package com.yesman.akythera.client.api.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.common.ThemeApplicableWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/// A number between two bounds, changed by dragging sideways across the box. A click without a drag
/// types the value instead. Drawn by a [DragValueBoxTheme].
@ScriptType
public class DragValueBox<T extends Number> extends AbstractWidget
    implements AnchoredWidget, DataBoundWidget<T>, ThemeApplicableWidget<DragValueBoxTheme>
{
    /// Pointer travel before a press turns into a drag, in widget pixels.
    private static final int DRAG_THRESHOLD = 3;

    private DragValueBoxTheme theme = BuiltInDragValueBoxTheme.DEFAULT_DRAG_VALUE_BOX;

    protected final Font font;
    private final T minValue;
    private final T maxValue;
    private final Function<T, String> serializer;
    private final DoubleFunction<T> fromDouble;

    /// Takes the typed text. The box is in typing mode while this is focused.
    private final AkytheraEditBox<T> editBox;

    private T value;

    /// Receives the value before and after a finished drag or typing.
    private @Nullable BiConsumer<T, T> onCommitted;

    /// Whether the left button went down on the box and is still held.
    private boolean pressed;

    /// Whether the held press has moved past [#DRAG_THRESHOLD].
    private boolean scrubbing;

    /// Pointer x the drag measures from, in screen pixels.
    private double pressX;

    /// The value when the press went down.
    private T pressValue;

    public DragValueBox(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final Callable<T> dataProvider,
        final Consumer<T> onWidgetChanged,
        final T minValue,
        final T maxValue,
        final Function<String, T> parser,
        final Function<T, String> serializer,
        final DoubleFunction<T> fromDouble,
        final Component message
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
        this.onValueChanged = onWidgetChanged;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.serializer = serializer;
        this.fromDouble = fromDouble;
        this.value = minValue;
        this.pressValue = minValue;

        editBox = createEditBox(
            () -> value,
            s -> s.isEmpty() || s.equals("-") || ParseUtils.isParsable(s, parser),
            parser,
            serializer,
            message
        );

        editBox.setOnCommitted((before, after) -> {
            T clamped = clamp(after.doubleValue());

            value = clamped;
            onValueChanged.accept(clamped);

            if (onCommitted != null) onCommitted.accept(before, clamped);
        });
    }

    /// Builds the box that takes the typed value. The box is laid over this one while typing.
    protected AkytheraEditBox<T> createEditBox(
        Callable<T> dataProvider,
        Predicate<String> filter,
        Function<String, T> parser,
        Function<T, String> serializer,
        Component message
    ) {
        return new AkytheraEditBox<>(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            dataProvider,
            _ -> {},
            filter,
            parser,
            serializer,
            message
        );
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (editBox.isFocused()) {
            editBox.setRectangle(getWidth(), getHeight(), getX(), getY());
            editBox.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        if (isActive() && (scrubbing || isHovered())) graphics.requestCursor(CursorTypes.RESIZE_EW);

        theme.render(this, graphics, font, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (editBox.isFocused()) return editBox.mouseClicked(event, doubleClick);
        if (!isActive() || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;

        pressed = true;
        scrubbing = false;
        pressX = event.x();
        pressValue = value;

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (editBox.isFocused()) return editBox.mouseDragged(event, dx, dy);
        if (!pressed || event.button() != 0) return false;

        if (!scrubbing) {
            if (Math.abs(event.x() - pressX) < DRAG_THRESHOLD) return true;

            scrubbing = true;
            pressX = event.x();
        }

        double range = maxValue.doubleValue() - minValue.doubleValue();
        T scrubbed = clamp(pressValue.doubleValue() + (event.x() - pressX) * range / Math.max(1, getWidth()));

        if (!scrubbed.equals(value)) {
            value = scrubbed;
            onValueChanged.accept(scrubbed);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (editBox.isFocused()) return editBox.mouseReleased(event);
        if (!pressed || event.button() != 0) return false;

        pressed = false;

        if (scrubbing) {
            scrubbing = false;

            if (onCommitted != null) onCommitted.accept(pressValue, value);
        } else {
            editBox.setValue(value);
            editBox.setRectangle(getWidth(), getHeight(), getX(), getY());
            editBox.setFocused(true);
            editBox.selectAll();
        }

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        return editBox.isFocused() && editBox.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return editBox.isFocused() && editBox.charTyped(event);
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return !editBox.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);

        if (!focused && editBox.isFocused()) editBox.setFocused(false);
    }

    @ScriptFunction
    public void setValue(final T value) {
        this.value = clamp(value.doubleValue());
    }

    @ScriptFunction(pure = true)
    public T getValue() {
        return value;
    }

    public void setOnCommitted(@Nullable BiConsumer<T, T> onCommitted) {
        this.onCommitted = onCommitted;
    }

    /// Whether the value is being dragged.
    public boolean isScrubbing() {
        return scrubbing;
    }

    /// Where the value sits between the bounds: 0 at the minimum, 1 at the maximum.
    public float getRatio() {
        double range = maxValue.doubleValue() - minValue.doubleValue();

        return range <= 0.0D ? 0.0F : (float) Math.clamp((value.doubleValue() - minValue.doubleValue()) / range, 0.0D, 1.0D);
    }

    /// The value as it is displayed.
    public String getText() {
        return serializer.apply(value);
    }

    /// `raw` inside the bounds, converted to `T`. NaN becomes the minimum.
    private T clamp(final double raw) {
        double min = minValue.doubleValue();

        return fromDouble.apply(Math.clamp(Double.isNaN(raw) ? min : raw, min, maxValue.doubleValue()));
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [ThemeApplicableWidget] implementations
    // ***************************************************************
    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return theme instanceof DragValueBoxTheme;
    }

    @Override
    public @NonNull DragValueBoxTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(DragValueBoxTheme theme) {
        this.theme = theme;
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
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

    // ***************************************************************
    // [DataBoundWidget] implementations
    // ***************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onValueChanged;

    @Override
    public Callable<T> getDataProvider() {
        return dataProvider;
    }

    @Override
    public Consumer<T> valueChangeCallback() {
        return onValueChanged;
    }

    @Override
    public Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        value = minValue;
    }
}
