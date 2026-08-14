package com.yesman.akythera.client.gui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.client.gui.widget.common.WarningMarkableWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/// A layout class for multiple widgets. Each widget must inherit [AbstractWidget] and [AnchoredWidget]
public class WidgetTable extends ContainerObjectSelectionList<WidgetTable.@NonNull WidgetEntry> implements AnchoredWidget {
    private static final int SCROLL_BAR_WIDTH = 5;

    private WidgetTable.@Nullable WidgetEntry lastEntry;

    private final Screen parentScreen;

    public WidgetTable(
        Screen parentScreen,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        AnchoredWidget.VerticalAnchorType verticalAnchor,
        int itemHeight
    ) {
        super(parentScreen.minecraft, 0, 0, 0, itemHeight);

        this.parentScreen = parentScreen;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
    }

    public Screen parentScreen() {
        return parentScreen;
    }

    @Override
    protected void extractListBackground(@NonNull GuiGraphicsExtractor guiGraphics) {
    }

    @Override
    protected void extractListSeparators(@NonNull GuiGraphicsExtractor guiGraphics) {
    }

    @Override
    public int getRowWidth() {
        return getWidth();
    }

    @Override
    protected int scrollBarX() {
        return getRight() - scrollbarWidth() - 2;
    }

    /// Ignores scroll bar setup
    @Override
    public int scrollbarWidth() {
        return SCROLL_BAR_WIDTH;
    }

    /// Ignores scroll bar setup
    @Override
    protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (scrollable()) {
            int scrollBarX = getRight() - SCROLL_BAR_WIDTH;
            int scrollerHeight = scrollerHeight();
            int scrollerY = scrollBarY();

            // scroll background
            graphics.fill(scrollBarX, getY(), scrollBarX + SCROLL_BAR_WIDTH, getBottom(), 0xFF1A1A1A);

            boolean hovering = (mouseX >= scrollBarX && mouseX < scrollBarX + SCROLL_BAR_WIDTH && mouseY >= scrollerY && mouseY < scrollerY + scrollerHeight);

            // scroll thumb
            graphics.fill(scrollBarX + 1, scrollerY, scrollBarX + SCROLL_BAR_WIDTH - 1, scrollerY + scrollerHeight, hovering ? 0xFF6A6A6A : 0xFF505050);

            if (isOverScrollbar(mouseX, mouseY)) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
    }

    public int nextX(int spacing) {
        if (lastEntry == null) {
            throw new IllegalStateException("Add an entry before calculating next space position");
        }

        int xPos;

        if (lastEntry.widgets.isEmpty()) {
            xPos = 0;
        } else {
            AbstractWidget lastWidget = lastEntry.widgets.getLast();
            xPos = lastWidget.getX() + lastWidget.getWidth();
        }

        return xPos + spacing;
    }

    public WidgetTable newRow() {
        return newRow(0);
    }

    /// Starts a row whose widgets anchor against the table rectangle inset by `indent` pixels on
    /// the left — nested rows (deeper property levels) shift right while right-anchored widgets
    /// stay put, so the depth a row belongs to reads off its left edge.
    public WidgetTable newRow(int indent) {
        lastEntry = new WidgetEntry(indent);
        addEntry(lastEntry);

        return this;
    }

    public <T extends AbstractWidget & AnchoredWidget> WidgetTable addWidget(T widget) {
        if (widget.getVerticalAnchorType() != VerticalAnchorType.TOP_HEIGHT) {
            throw new IllegalArgumentException("Only TOP_HEIGHT vertical anchor is allowed in WidgetTable");
        }

        Objects.requireNonNull(lastEntry);

        lastEntry.widgets.add(widget);
        lastEntry.anchored.add(widget);

        if (widget instanceof DataBoundWidget<?> dataBoundWidget) lastEntry.dataBound.add(dataBoundWidget);

        return this;
    }

    /// Initialize widget that inherits [DataBoundWidget]
    @SuppressWarnings("unchecked")
    public void initialize(boolean markNoValues) {
        for (WidgetEntry widgetEntry : children()) {
            for (DataBoundWidget<?> widget : widgetEntry.dataBound) {
                Callable<?> callable = widget.getDataProvider();

                try {
                    Object value = callable.call();
                    ((DataBoundWidget<Object>)widget).valueSetter().accept(value);

                    if (value == null) {
                        if (markNoValues && widget instanceof WarningMarkableWidget<?> warningMarkableWidget) warningMarkableWidget.mark();
                    }
                } catch (Exception e) { // In case exception caught while trying to get value such as no such element in json or compound tag.
                    if (markNoValues && widget instanceof WarningMarkableWidget<?> warningMarkableWidget) warningMarkableWidget.mark();
                }
            }
        }
    }

    /// Resets widget that inherits [DataBoundWidget]
    /// See with the comment at [DataBoundWidget#reset]
    public void resetWidgets() {
        for (WidgetEntry widgetEntry : children()) {
            for (DataBoundWidget<?> dataBoundwidget : widgetEntry.dataBound()) {
                dataBoundwidget.reset();
            }
        }
    }

    /// Removes all widgets in table
    public void clearWidgets() {
        clearEntries();
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        int y = getY() + 2 - (int)scrollAmount();

        for (WidgetEntry child : children()) {
            child.setY(y);
            y += child.getHeight();
            child.setX(getRowLeft());
            child.setWidth(getRowWidth());
        }

        ScreenRectangle tableRectangle = new ScreenRectangle(getX(), getY(), getWidth(), getHeight());
        int widgetIndex = 0;

        for (WidgetEntry widgetEntry : children()) {
            ScreenRectangle rowRectangle = widgetEntry.indent == 0
                ? tableRectangle
                : new ScreenRectangle(
                    tableRectangle.left() + widgetEntry.indent, tableRectangle.top(),
                    Math.max(0, tableRectangle.width() - widgetEntry.indent), tableRectangle.height());

            for (AnchoredWidget widget : widgetEntry.anchored()) {
                int height = widget.getVerticalAnchorType().scaler().withSize(rowRectangle, widget);

                // Locate the widget at the vertical center of the current row, as we don't allow anchoring
                // rather than VerticalAnchorType.TOP_HEIGHT in addWidget(), the operation is expected to work
                // as same as AbstractWidget#setY()
                int top = getRowTop(widgetIndex) + (defaultEntryHeight - height) / 2 - rowRectangle.top();
                widget.setY1(AnchoredWidget.pixel(top));
                widget.relocate(rowRectangle);
            }

            widgetIndex++;
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        updateScrolling(event);

        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double xDelta, double yDelta) {
        // Checks the focusing entry first for the cases that widgets could be pressed out of the row bounds (e.g. combo box dropdown)
        if (getFocused() != null) {
            int index = children().indexOf(getFocused());

            if (index >= 0) {
                int focusTop = getRowTop(index);
                int focusBottom = getRowBottom(index);

                // When the focused entry is in a visible area
                if (focusTop <= getBottom() && focusBottom >= getY()) {
                    for (GuiEventListener e : getFocused().children()) {
                        if (e.mouseScrolled(x, y, xDelta, yDelta)) {
                            return true;
                        }
                    }
                }
            }
        }

        // Checks the entry in the mouse pointer location
        WidgetEntry e = getEntryAtPosition(x, y);

        if (e != null && e.mouseScrolled(x, y, xDelta, yDelta)) {
            return true;
        }

        return super.mouseScrolled(x, y, xDelta, yDelta);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        for (int i = 0; i < children().size(); i++) {
            WidgetEntry entry = children().get(i);
            int j1 = getRowTop(i);
            int k1 = getRowBottom(i);

            if (k1 >= getY() && j1 <= getBottom()) {
                if (entry.getChildAt(event.x(), event.y()).filter(component -> component.mouseDragged(event, dx, dy)).isPresent()) {
                    return true;
                }
            }
        }

        return super.mouseDragged(event, dx, dy);
    }

    public class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetTable.@NonNull WidgetEntry> {
        final List<AbstractWidget> widgets = new ArrayList<> ();
        final List<AnchoredWidget> anchored = new ArrayList<> ();
        final List<DataBoundWidget<?>> dataBound = new ArrayList<> ();

        /// Left inset in pixels the row's widgets anchor against, see [WidgetTable#newRow(int)].
        final int indent;

        WidgetEntry(int indent) {
            this.indent = indent;
        }

        @Override
        public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
            for (GuiEventListener widget : widgets) {
                if (widget.isMouseOver(x, y)) {
                    return Optional.of(widget);
                }
            }

            return Optional.empty();
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return widgets;
        }

        public List<? extends AnchoredWidget> anchored() {
            return anchored;
        }

        public List<? extends DataBoundWidget<?>> dataBound() {
            return dataBound;
        }
        
        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            if (getFocused() == focused) return;

            if (focused != null && !focused.isFocused()) {
                super.setFocused(null);
            }

            super.setFocused(focused);
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float a) {
            for (AbstractWidget widget : widgets) {
                widget.setY(getContentY() + WidgetTable.this.defaultEntryHeight / 2 - widget.getHeight() / 2);
                widget.extractRenderState(guiGraphics, mouseX, mouseY, a);
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
}
