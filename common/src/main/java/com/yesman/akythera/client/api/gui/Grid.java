package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// A scrollable grid laying its children out left-to-right, top-to-bottom in attachment order, one per cell.
/// An [AnchoredWidget] child is relocated inside its cell, so its anchors apply there; any other child fills
/// its cell.
@ScriptType
public class Grid extends AkytheraWidget {
    private static final int SCROLLBAR_W = 5;

    /// Cell width, in pixels, unless [#cellWidth] is overridden.
    private final int cellWidth;

    /// Cell height, in pixels, unless [#cellHeight] is overridden.
    private final int cellHeight;

    /// Space between the cells and around them, in both directions, in pixels.
    private final int padding;

    @Nullable
    private final Comparator<AbstractWidget> comparator;

    /// Height of the laid out cells, from the last [#relocateChildren].
    private int totalHeight;

    public Grid(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        final AnchoredWidget.VerticalAnchorType verticalAnchor,
        final int cellWidth,
        final int cellHeight,
        final int padding,
        final @Nullable Comparator<AbstractWidget> comparator
    ) {
        super(x1, x2, y1, y2, horizontalAnchor, verticalAnchor);

        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.padding = padding;
        this.comparator = comparator;
    }

    /// Sorts the children by the stored comparator.
    public void sort() {
        if (comparator != null) sortComponents(comparator);
    }

    /// Width of a cell while the grid occupies `gridRectangle`.
    protected int cellWidth(ScreenRectangle gridRectangle) {
        return cellWidth;
    }

    /// Height of a cell while the grid occupies `gridRectangle`.
    protected int cellHeight(ScreenRectangle gridRectangle) {
        return cellHeight;
    }

    /// Places every child in its cell, shifted up by the scroll amount.
    @Override
    public void relocateChildren() {
        List<AbstractWidget> children = children();
        ScreenRectangle gridRectangle = getRectangle();
        int cellW = cellWidth(gridRectangle);
        int cellH = cellHeight(gridRectangle);
        int contentRight = getX() + getWidth() - SCROLLBAR_W;
        int x = getX() + padding;
        int y = getY() - (int)scrollAmount() + padding;
        int rows = 1;

        for (AbstractWidget cell : children) {
            placeChild(cell, new ScreenRectangle(x, y, cellW, cellH));

            if (x + (cellW * 2 + padding) > contentRight) { // Checks the next widget start exceeds the grid right
                // Next row first
                x = getX() + padding;
                y += (cellH + padding);
                if (cell != children.getLast()) rows++;
            } else {
                // Next column
                x += (cellW + padding);
            }
        }

        totalHeight = (cellH + padding) * rows - padding;
    }

    @Override
    public void setScrollAmount(double scrollAmount) {
        super.setScrollAmount(scrollAmount);
        relocateChildren();
    }

    /// Lays the cells out against the new rect, keeping the scroll ratio.
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        double scrollRatio = maxScrollAmount() == 0 ? 0.0D : scrollAmount() / maxScrollAmount();
        super.relocate(screenRectangle);

        double scroll = maxScrollAmount() * scrollRatio;
        if (scroll != scrollAmount()) setScrollAmount(scroll);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        List<AbstractWidget> children = children();

        if (children.isEmpty()) {
            return;
        }

        graphics.enableScissor(getX(), getY(), getRight() - SCROLLBAR_W, getBottom());

        for (AbstractWidget cell : children) {
            if (cell.getBottom() >= getY() && cell.getY() <= getBottom()) { // Checks whether the grid cell in visible area
                cell.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        graphics.disableScissor();

        if (scrollable()) {
            renderGridScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderGridScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int scrollBarX = getX() + getWidth() - SCROLLBAR_W;
        int trackTop = getY();
        int trackH = getHeight();
        int maxScroll = maxScrollAmount();

        graphics.fill(scrollBarX, trackTop, scrollBarX + SCROLLBAR_W, trackTop + trackH, 0xFF1A1A1A);

        int thumbH = Math.max(8, trackH * trackH / contentHeight());
        int thumbY = maxScroll > 0
            ? trackTop + (trackH - thumbH) * (int)scrollAmount() / maxScroll
            : trackTop;

        boolean hovering = mouseX >= scrollBarX && mouseX < scrollBarX + SCROLLBAR_W && mouseY >= trackTop && mouseY < trackTop + trackH;

        graphics.fill(scrollBarX + 1, thumbY, scrollBarX + SCROLLBAR_W - 1, thumbY + thumbH, hovering ? 0xFF6A6A6A : 0xFF505050);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        Optional<GuiEventListener> child = getChildAt(event.x(), event.y());

        if (child.isEmpty()) {
            return false;
        } else {
            GuiEventListener widget = child.get();

            if (widget.mouseClicked(event, doubleClick) && widget.shouldTakeFocusAfterInteraction()) {
                setFocused(widget);

                if (event.button() == 0) {
                    setDragging(true);
                }
            }

            return true;
        }
    }

    /// Hands the wheel to the cell under the pointer first, then scrolls the grid by whole rows.
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mx, my) || children().isEmpty()) {
            return false;
        }

        if (getChildAt(mx, my).filter(child -> child.mouseScrolled(mx, my, scrollX, scrollY)).isPresent()) {
            return true;
        }

        int rowStride = cellHeight(getRectangle()) + padding;
        setScrollAmount(scrollAmount() - scrollY * rowStride);

        return true;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    protected int contentHeight() {
        return totalHeight;
    }

    @Override
    protected int scrollBarX() {
        return getRight() - SCROLLBAR_W;
    }

    @Override
    public int scrollbarWidth() {
        return SCROLLBAR_W;
    }
}
