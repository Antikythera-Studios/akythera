package com.yesman.akythera.client.gui.widget;


import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.client.gui.widget.common.PressableWidget;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ComboBox<T> extends AbstractWidget implements AnchoredWidget, DataBoundWidget<T>, PressableWidget {
    private final ComboItemList comboItemList;
    private final Font font;
    private final int maxRows;
    private final Supplier<ScreenRectangle> ownerBoundsSupplier;

    public ComboBox(
        Screen parent,
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        AnchoredWidget.VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider,
        Consumer<T> onWidgetChanged,
        int maxRows,
        Component title,
        Collection<T> items,
        Function<T, String> displayStringMapper,
        Supplier<ScreenRectangle> ownerBoundsSupplier
    ) {
        super(0, 0, 0, 0, title);

        this.font = font;
        this.maxRows = Math.min(maxRows, items.size());
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;

        this.ownerBoundsSupplier = ownerBoundsSupplier;
        this.comboItemList = new ComboItemList(parent.minecraft, this.maxRows, 15);

        for (T item : items) {
            this.comboItemList.addEntry(item, displayStringMapper.apply(item));
        }

        this.comboItemList.visible = false;
        this.comboItemList.active = false;

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;

        relocateComboList();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) {
            if (comboItemList.isActive() && comboItemList.mouseClicked(event, doubleClick)) {
                if (event.x() < comboItemList.scrollBarX() || event.x() > comboItemList.scrollBarX() + 6) {
                    comboItemList.active = false;
                    comboItemList.visible = false;
                }

                playDownSound(Minecraft.getInstance().getSoundManager());

                return true;
            } else {
                if (isValidClickButton(event.buttonInfo())) {
                    boolean flag = clickedMainWidget(event.x(), event.y());

                    if (flag) {
                        if (arrowClicked(event.x(), event.y())) {
                            onPressed();
                        } else {
                            closeSelectionListIfActive();
                        }

                        return true;
                    }
                }
            }

            return false;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double xDelta, double yDelta) {
        if (comboItemList.isActive()) {
            return comboItemList.mouseScrolled(x, y, xDelta, yDelta);
        }

        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (comboItemList.isActive()) {
            return comboItemList.mouseDragged(event, dx, dy);
        }

        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (active && visible) {
            if (event.isSelection()) {
                onPressed();
                return true;
            } else if (isSelectionListVisible()) {
                switch (event.key()) {
                    case InputConstants.KEY_DOWN -> {
                        int i = Math.min(comboItemList.children().size() - 1, comboItemList.children().indexOf(comboItemList.getSelected()) + 1);
                        comboItemList.setSelected(comboItemList.children().get(i));
                        return true;
                    }
                    case InputConstants.KEY_UP -> {
                        int i = Math.max(0, comboItemList.children().indexOf(comboItemList.getSelected()) - 1);
                        comboItemList.setSelected(comboItemList.children().get(i));
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isSelectionListVisible() {
        return comboItemList.isActive() && comboItemList.visible;
    }

    public void setSelectionListVisible(boolean flag) {
        comboItemList.active = flag;
        comboItemList.visible = flag;
    }

    private boolean clickedMainWidget(double x, double y) {
        return active && visible && x >= (double) getX() && y >= (double) getY() && x < (double) (getX() + getWidth()) && y < (double) (getY() + getHeight());
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        if (isSelectionListVisible() && comboItemList.isMouseOver(x, y)) {
            return true;
        }

        return isActive() && visible && x >= (double) getX() && y >= (double) getY() && x < (double) (getX() + getWidth()) && y < (double) (getY() + getHeight() * (maxRows + 1));
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        int left = comboItemList.getX();
        comboItemList.setWidth(width);
        comboItemList.setX(left);
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        relocateComboList();
    }

    private void relocateComboList() {
        int entryHeight = 15;
        int dropdownHeight = entryHeight * maxRows;
        ScreenRectangle ownerBounds = ownerBoundsSupplier.get();
        int spaceBelow = ownerBounds.bottom() - (getY() + height + 1);
        int spaceAbove = getY() - ownerBounds.top() - 1;

        if (spaceBelow < dropdownHeight && spaceAbove >= dropdownHeight) {
            comboItemList.updateSizeAndPosition(width, dropdownHeight, getY() - (dropdownHeight + 1));
        } else {
            comboItemList.updateSizeAndPosition(width, dropdownHeight, getY() + height + 1);
        }

        comboItemList.setX(getX());
    }

    private void openSelectionList() {
        playDownSound(Minecraft.getInstance().getSoundManager());
        setSelectionListVisible(!isSelectionListVisible());
    }

    private void closeSelectionListIfActive() {
        if (!isSelectionListVisible()) {
            return;
        }

        setSelectionListVisible(false);
        playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    public void onPressed() {
        if (isSelectionListVisible()) {
            closeSelectionListIfActive();
        } else {
            openSelectionList();
        }
    }

    private boolean arrowClicked(double x, double y) {
        int openPressed = getX() + width - 14;
        return active && visible && x >= (double)openPressed && y >= (double) getY() && x < (double) (getX() + width) && y < (double) (getY() + height);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        int outlineColor = isFocused() ? -1 : isActive() ? -6250336 : -12566463;

        guiGraphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, outlineColor);
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, -16777216);

        String correctedString = font.plainSubstrByWidth(comboItemList.getSelected() == null ? "" : comboItemList.getSelected().displayName, width - 10);
        int fontColor = isActive() ? 16777215 : 4210752;

        guiGraphics.text(font, Component.literal(correctedString), getX() + 4, getY() + height / 2 - font.lineHeight / 2 + 1, fontColor, false);
        guiGraphics.text(font, Component.literal("▼"), getX() + width - 8, getY() + height / 2 - font.lineHeight / 2 + 1, fontColor, false);

        if (comboItemList.visible) {
            ((GuiGraphicsAccessor)guiGraphics).akythera$setDeferredWidgetRenderer(() -> comboItemList.extractRenderState(guiGraphics, mouseX, mouseY, a));
        }
    }

    public void setValue(T value) {
        comboItemList.setSelected(value);
        valueChangeCallback().accept(value);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        comboItemList.setFocused(focused);

        if (!focused) {
            setSelectionListVisible(false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementInput) {
        narrationElementInput.add(NarratedElementType.TITLE, createNarrationMessage());
    }

    class ComboItemList extends ObjectSelectionList<ComboItemList.@NonNull ComboItemEntry> {
        private final Map<T, ComboItemEntry> entryMap = new HashMap<>();

        public ComboItemList(Minecraft minecraft, int maxRows, int itemHeight) {
            super(minecraft, ComboBox.this.getWidth(), itemHeight * maxRows, 0, itemHeight);
        }

        public void addEntry(T item, String displayName) {
            ComboItemEntry entry = new ComboItemEntry(item, displayName);
            entryMap.put(item, entry);
            addEntry(entry);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
            guiGraphics.fill(getX() - 1, getY() - 1, getRight() + 1, getBottom() + 1, -1);
            guiGraphics.fill(getX(), getY(), getRight(), getBottom(), -16777216);

            enableScissor(guiGraphics);
            extractListItems(guiGraphics, mouseX, mouseY, a);
            guiGraphics.disableScissor();
        }

        public void setSelected(@Nullable T item) {
            setSelected(entryMap.get(item));
        }

        // Widen visibility: protected -> public
        @Override
        public int scrollBarX() {
            return super.scrollBarX();
        }

        @Override
        public int getRowWidth() {
            return ComboBox.this.width;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);

            // Hide widget when focus removed
            if (!focused) {
                active = false;
                visible = false;
            }
        }

        class ComboItemEntry extends ObjectSelectionList.Entry<ComboItemList.@NonNull ComboItemEntry> {
            private final T item;
            private final String displayName;

            protected ComboItemEntry(T item, String displayName) {
                this.item = item;
                this.displayName = displayName;
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() == 0) {
                    setSelected(this);
                    ComboBox.this.setValue(item);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public @NonNull Component getNarration() {
                return Component.empty();
            }

            public T getItem() {
                return item;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float a) {
                guiGraphics.text(ComboBox.this.font, displayName, getContentX() + 2, getContentY() + 1, 16777215, false);
            }
        }
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

    /// ***************************************************************
    /// [DataBoundWidget] implementations                             *
    /// ***************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onWidgetChanged;

    @Override
    public Callable<T> getDataProvider() {
        return this.dataProvider;
    }

    @Override
    public Consumer<T> valueChangeCallback() {
        return this.onWidgetChanged;
    }

    @Override
    public Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        this.comboItemList.setSelected((T)null);
    }
}
