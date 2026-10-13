package com.yesman.akythera.client.api.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.common.WarningMarkableWidget;
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
import java.util.function.IntConsumer;

/// A layout class for multiple widgets. Each widget must inherit [AbstractWidget] and [AnchoredWidget]
public class WidgetTable extends ContainerObjectSelectionList<WidgetTable.@NonNull WidgetEntry> implements AnchoredWidget {
    private static final int SCROLL_BAR_WIDTH = 5;
    /// Space between the title column and the widgets right of it, in pixels.
    private static final int TITLE_GAP = 4;
    private static final int PRESSED_ROW_COLOR = 0xFF1E4D72;
    private static final int HOVERED_ROW_COLOR = 0xFF262626;

    /// Space above and below the widgets of a [#resizable] row, in pixels.
    private static final int ROW_MARGIN = 2;

    /// Height of the band along a [#resizable] row's bottom edge that drags it, in pixels.
    private static final int GRIP = 3;

    private WidgetTable.@Nullable WidgetEntry lastEntry;

    /// The row [#onRowPressed] last ran for, drawn highlighted. Null until one is pressed.
    private WidgetTable.@Nullable WidgetEntry pressedEntry;

    private final Screen parentScreen;

    /// Narrowest the title column gets, in pixels from the table's left edge.
    private int minTitleWidth;

    /// The [#resizable] row whose bottom edge is being dragged. Null while none is.
    private WidgetTable.@Nullable WidgetEntry resizingEntry;

    /// The rect the table was last relocated against, so a resized row can lay the table out again.
    private @Nullable ScreenRectangle parentRectangle;

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

    /// Sets the narrowest the title column gets, in pixels. Half the table width still caps it.
    public void setMinTitleWidth(int minTitleWidth) {
        this.minTitleWidth = minTitleWidth;
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
        lastEntry = new WidgetEntry(indent, null);
        addEntry(lastEntry);

        return this;
    }

    public WidgetTable newRow(Static title) {
        return newRow(0, title);
    }

    /// Starts a row led by `title`. All titles share one column, as wide as the widest title (at
    /// least [#setMinTitleWidth]) and at most half the table width. The title anchors against its
    /// cell in that column, inset by `indent`; the row's other widgets anchor against the area right
    /// of the column.
    public WidgetTable newRow(int indent, Static title) {
        if (title.getVerticalAnchorType() != VerticalAnchorType.TOP_HEIGHT) {
            throw new IllegalArgumentException("Only TOP_HEIGHT vertical anchor is allowed in WidgetTable");
        }

        lastEntry = new WidgetEntry(indent, title);
        lastEntry.widgets.add(title);
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

    /// Makes the current row `height` pixels tall, resizable by dragging its bottom edge down to `minHeight`.
    /// Its widgets stretch to the row's height. `onResized`, if any, receives the height a drag settles on.
    public WidgetTable resizable(int height, int minHeight, @Nullable IntConsumer onResized) {
        WidgetEntry entry = Objects.requireNonNull(lastEntry);

        entry.minHeight = minHeight;
        entry.onResized = onResized;
        entry.setHeight(Math.max(height, minHeight));

        return this;
    }

    /// Makes the current row pressable: a press on it outside its widgets runs `onPressed` and
    /// highlights the row until another row is pressed.
    public WidgetTable onRowPressed(Runnable onPressed) {
        Objects.requireNonNull(lastEntry).onPressed = onPressed;

        return this;
    }

    /// Runs `onFocused` whenever one of the current row's widgets takes focus.
    public WidgetTable onRowFocused(Runnable onFocused) {
        Objects.requireNonNull(lastEntry).onFocused = onFocused;

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
        pressedEntry = null;
        resizingEntry = null;
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        parentRectangle = screenRectangle;
        AnchoredWidget.super.relocate(screenRectangle);

        int y = getY() + 2 - (int)scrollAmount();

        for (WidgetEntry child : children()) {
            child.setY(y);
            y += child.getHeight();
            child.setX(getRowLeft());
            child.setWidth(getRowWidth());
        }

        ScreenRectangle tableRectangle = new ScreenRectangle(getX(), getY(), getWidth(), getHeight());
        int titleColumn = minTitleWidth;

        for (WidgetEntry widgetEntry : children()) {
            if (widgetEntry.title != null) {
                int titleWidth = widgetEntry.indent + widgetEntry.title.getX1().pixels(tableRectangle, true) + widgetEntry.title.textWidth();
                titleColumn = Math.max(titleColumn, titleWidth);
            }
        }

        titleColumn = Math.min(titleColumn, tableRectangle.width() / 2);

        ScreenRectangle contentRectangle = new ScreenRectangle(
            tableRectangle.left() + titleColumn + TITLE_GAP, tableRectangle.top(),
            Math.max(0, tableRectangle.width() - titleColumn - TITLE_GAP), tableRectangle.height());
        int widgetIndex = 0;

        for (WidgetEntry widgetEntry : children()) {
            ScreenRectangle rowRectangle = widgetEntry.indent == 0
                ? tableRectangle
                : new ScreenRectangle(
                    tableRectangle.left() + widgetEntry.indent, tableRectangle.top(),
                    Math.max(0, tableRectangle.width() - widgetEntry.indent), tableRectangle.height());

            if (widgetEntry.title != null) {
                ScreenRectangle titleCell = new ScreenRectangle(
                    rowRectangle.left(), rowRectangle.top(),
                    Math.max(0, titleColumn - widgetEntry.indent), rowRectangle.height());

                placeInRow(widgetEntry.title, titleCell, widgetEntry, widgetIndex);
                rowRectangle = contentRectangle;
            }

            for (AnchoredWidget widget : widgetEntry.anchored()) {
                placeInRow(widget, rowRectangle, widgetEntry, widgetIndex);
            }

            widgetIndex++;
        }
    }

    /// Relocates `widget` against `cell`, centered vertically on `entry`, row `rowIndex`. A [#resizable] row
    /// stretches it to its own height first.
    private void placeInRow(AnchoredWidget widget, ScreenRectangle cell, WidgetEntry entry, int rowIndex) {
        if (entry.minHeight > 0) widget.setY2(AnchoredWidget.pixel(entry.getHeight() - ROW_MARGIN * 2));

        int height = widget.getVerticalAnchorType().scaler().withSize(cell, widget);

        // Locate the widget at the vertical center of the current row, as we don't allow anchoring
        // rather than VerticalAnchorType.TOP_HEIGHT in addWidget(), the operation is expected to work
        // as same as AbstractWidget#setY()
        int top = getRowTop(rowIndex) + (entry.getHeight() - height) / 2 - cell.top();
        widget.setY1(AnchoredWidget.pixel(top));
        widget.relocate(cell);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        updateScrolling(event);

        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }

        if (event.button() == 0) {
            for (WidgetEntry entry : children()) {
                if (entry.isOverGrip(event.x(), event.y())) {
                    resizingEntry = entry;
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (resizingEntry != null) {
            if (resizingEntry.onResized != null) resizingEntry.onResized.accept(resizingEntry.getHeight());

            resizingEntry = null;
            return true;
        }

        return super.mouseReleased(event);
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
        if (resizingEntry != null) {
            int height = (int) event.y() - resizingEntry.getY();

            resizingEntry.setHeight(Math.clamp(height, resizingEntry.minHeight, Math.max(resizingEntry.minHeight, getHeight())));

            if (parentRectangle != null) relocate(parentRectangle);

            return true;
        }

        WidgetEntry focused = getFocused();

        // The widget holding the drag keeps it after the pointer leaves its bounds.
        if (focused != null && focused.isDragging() && focused.getFocused() != null && focused.getFocused().mouseDragged(event, dx, dy)) {
            return true;
        }

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
        /// The row's title, laid out in the table's title column. Null for a row without one.
        final @Nullable Static title;
        /// Runs on a press outside the row's widgets, see [WidgetTable#onRowPressed]. Null for a row
        /// that takes no press.
        @Nullable Runnable onPressed;

        /// Runs when one of the row's widgets takes focus, see [WidgetTable#onRowFocused]. Null when nothing listens.
        @Nullable Runnable onFocused;

        /// The shortest a [WidgetTable#resizable] row gets, in pixels. 0 for a row of the default height.
        int minHeight;

        /// Receives the height a drag of a [WidgetTable#resizable] row settles on. Null when nothing listens.
        @Nullable IntConsumer onResized;

        WidgetEntry(int indent, @Nullable Static title) {
            this.indent = indent;
            this.title = title;
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
            if (getFocused() == focused && (focused == null || focused.isFocused())) return;

            if (focused != null && !focused.isFocused()) {
                super.setFocused(null);
            }

            super.setFocused(focused);

            if (focused != null && focused.isFocused() && onFocused != null) onFocused.run();
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) return true;
            if (onPressed == null || event.button() != 0) return false;

            pressedEntry = this;
            onPressed.run();

            return true;
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float a) {
            if (this == pressedEntry) {
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), PRESSED_ROW_COLOR);
            } else if (onPressed != null && hovering) {
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), HOVERED_ROW_COLOR);
            }

            for (AbstractWidget widget : widgets) {
                widget.setY(minHeight > 0
                    ? getY() + (getHeight() - widget.getHeight()) / 2
                    : getContentY() + WidgetTable.this.defaultEntryHeight / 2 - widget.getHeight() / 2);
                widget.extractRenderState(guiGraphics, mouseX, mouseY, a);
            }

            if (minHeight > 0) {
                int centerX = getX() + getWidth() / 2;
                int bottom = getY() + getHeight();

                guiGraphics.fill(centerX - 8, bottom - 1, centerX + 8, bottom, 0xFF505050);

                if (this == resizingEntry || isOverGrip(mouseX, mouseY)) guiGraphics.requestCursor(CursorTypes.RESIZE_NS);
            }
        }

        /// Whether the point is on the band along a [WidgetTable#resizable] row's bottom edge.
        boolean isOverGrip(double x, double y) {
            int bottom = getY() + getHeight();

            return minHeight > 0 && x >= getX() && x < getX() + getWidth() && y >= bottom - GRIP && y <= bottom + 1;
        }
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
