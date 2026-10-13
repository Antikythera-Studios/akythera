package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// A swatch showing an [RGBA]. Pressing it opens a [ColorSelectorModal] under it; pressed again while that is
/// open, it closes it. Every change in the selector reaches the change callback; closing it reaches
/// [#onCommitted] once, with the color it opened on and the one it closed on.
@ScriptType
public class ColorPickerWidget extends AbstractWidget implements AnchoredWidget, DataBoundWidget<RGBA> {
    /// Edge of a checkerboard cell behind a translucent color, in pixels.
    private static final int CHECKER = 4;

    protected final Font font;
    private RGBA value = new RGBA(1.0F, 1.0F, 1.0F);

    /// Called when a selector that changed the color closes, with the color it opened on and the one it closed on.
    /// Null calls nothing.
    @Nullable
    private BiConsumer<RGBA, RGBA> onCommitted;

    /// The selector this swatch opened last, so a second press closes it.
    @Nullable
    private ColorSelectorModal selector;

    public ColorPickerWidget(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final Callable<RGBA> dataProvider,
        final Consumer<RGBA> onValueChanged
    ) {
        super(0, 0, 0, 0, Component.empty());

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onValueChanged = onValueChanged;
    }

    @ScriptFunction(pure = true)
    public RGBA getValue() {
        return value;
    }

    /// Sets the color without calling the change callback.
    @ScriptFunction
    public void setValue(final RGBA value) {
        this.value = value;
    }

    /// Sets [#onCommitted].
    public void setOnCommitted(final @Nullable BiConsumer<RGBA, RGBA> onCommitted) {
        this.onCommitted = onCommitted;
    }

    /// Builds the selector opened under this swatch, starting from `initial`, handing every change to
    /// `onChanged` and running `onClosed` when it closes.
    protected ColorSelectorModal createSelector(final RGBA initial, final Consumer<RGBA> onChanged, final Runnable onClosed) {
        return new ColorSelectorModal(font, initial, onChanged, onClosed);
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        ModalManager modals = ModalManager.getInstance();

        if (selector != null && modals.getModal() == selector) {
            modals.closeModal();
            return;
        }

        RGBA opened = value;

        selector = createSelector(
            opened,
            color -> {
                value = color;
                onValueChanged.accept(color);
            },
            () -> {
                if (onCommitted != null && value != opened) onCommitted.accept(opened, value);
            }
        );
        modals.openModalWidgetUnder(this, selector, false);
        playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = getX();
        int top = getY();
        int right = getRight();
        int bottom = getBottom();

        for (int y = top; y < bottom; y += CHECKER) {
            for (int x = left; x < right; x += CHECKER) {
                boolean light = ((x - left) / CHECKER + (y - top) / CHECKER) % 2 == 0;
                graphics.fill(x, y, Math.min(x + CHECKER, right), Math.min(y + CHECKER, bottom), light ? 0xFFC0C0C0 : 0xFF808080);
            }
        }

        graphics.fill(left, top, right, bottom, value.packARGB());
        graphics.outline(left, top, getWidth(), getHeight(), isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF555555);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
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
    private final Callable<RGBA> dataProvider;
    private final Consumer<RGBA> onValueChanged;

    @Override
    public Callable<RGBA> getDataProvider() {
        return dataProvider;
    }

    @Override
    public Consumer<RGBA> valueChangeCallback() {
        return onValueChanged;
    }

    @Override
    public Consumer<RGBA> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        value = new RGBA(1.0F, 1.0F, 1.0F);
    }
}
