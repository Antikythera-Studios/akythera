package com.yesman.akythera.client.api.gui.modal;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ToIntFunction;

/// Edge-drag resizing for a modal frame: the grip hit-testing, the drag arithmetic, the cursor
/// affordance, the hand-off of the size the user settled on, and the width fit to a list's rows in view.
/// **Held** by the widget it resizes rather than inherited (see [ResizableFrame]), so each dropdown keeps
/// its own class shape and only routes its mouse / render calls in.
///
/// The frame must be an [AnchoredWidget] anchored by its size — `LEFT_WIDTH` or `CENTER_WIDTH`, and
/// `TOP_HEIGHT` or `CENTER_HEIGHT`: a new rect is applied through the anchor units followed by
/// [AnchoredWidget#relocate], which is the only sizing channel that survives — relocate re-derives the
/// rect from those units, so writing the widget bounds directly is undone by the next one.
public final class FrameResizer {
    /// Width of the band along each frame edge that grabs it. The band sits **outside** the frame, so
    /// it can never swallow a click meant for the modal's content (an entry list, its scrollbar); the
    /// cost is that a press within this much of the frame resizes instead of dismissing the modal.
    private static final int GRIP = 4;

    /// Sink for a size the frame was dragged to — reported once per drag, when the grip is released.
    public interface SizeSink {
        void accept(int width, int height);
    }

    /// The frame edge a drag is moving, one per axis — both are set at once for a corner grab.
    private enum Grip { NONE, LEFT, RIGHT, TOP, BOTTOM }

    private final AbstractWidget frame;
    private final AnchoredWidget anchored;
    private final int minWidth;
    private final int minHeight;

    private Grip horizontalGrip = Grip.NONE;
    private Grip verticalGrip = Grip.NONE;

    /// Distance from the grab point to the edge it grabbed, so the edge tracks the cursor exactly
    /// instead of snapping to it on the first drag event.
    private int gripOffsetX;
    private int gripOffsetY;

    /// Width the frame was opened or dragged to, before [#fitWidthToVisibleRows] widens it. 0 until the
    /// first fit or horizontal drag.
    private int baseWidth;
    private int baseX;

    /// Width the rows in view need, from the last [#fitWidthToVisibleRows]. A drag can't shrink the frame
    /// below it.
    private int contentWidth;

    private @Nullable SizeSink sizeSink;

    public <F extends AbstractWidget & AnchoredWidget> FrameResizer(F frame, int minWidth, int minHeight) {
        this.frame = frame;
        this.anchored = frame;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    /// Where to report a size the user drags the frame to. Pointed at the anchor's
    /// [DropdownSizeMemory] by [ModalManager#openModalWidgetUnder], so the next open
    /// restores it; null (the default) simply forgets the size when the modal closes.
    public void setSizeSink(@Nullable SizeSink sizeSink) {
        this.sizeSink = sizeSink;
    }

    /// Grabs the frame edge(s) under the press, if any — both axes when the press is in a corner — and
    /// remembers how far each grab sits from its edge. False when the press is not on the frame, so the
    /// caller routes it as ordinary content input (or a dismissal).
    public boolean mousePressed(double mouseX, double mouseY) {
        horizontalGrip = Grip.NONE;
        verticalGrip = Grip.NONE;

        // Only the frame's own span (plus the grip band at its ends) grabs, so a press far off one side
        // can't seize the other axis' edge.
        boolean alongRows = mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
        boolean alongColumns = mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP;

        if (alongRows && inBandBefore(mouseX, frame.getX())) {
            horizontalGrip = Grip.LEFT;
            gripOffsetX = frame.getX() - (int) mouseX;
        } else if (alongRows && inBandAfter(mouseX, frame.getRight())) {
            horizontalGrip = Grip.RIGHT;
            gripOffsetX = frame.getRight() - (int) mouseX;
        }

        if (alongColumns && inBandBefore(mouseY, frame.getY())) {
            verticalGrip = Grip.TOP;
            gripOffsetY = frame.getY() - (int) mouseY;
        } else if (alongColumns && inBandAfter(mouseY, frame.getBottom())) {
            verticalGrip = Grip.BOTTOM;
            gripOffsetY = frame.getBottom() - (int) mouseY;
        }

        return resizing();
    }

    /// Moves the grabbed edge(s) to the cursor, the opposite ones staying put. False when no grip is
    /// held, so the caller can fall through to its normal drag handling.
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!resizing()) return false;

        int left = frame.getX();
        int top = frame.getY();
        int right = frame.getRight();
        int bottom = frame.getBottom();

        switch (horizontalGrip) {
            case LEFT -> left = Math.min((int) mouseX + gripOffsetX, right - Math.max(minWidth, contentWidth));
            case RIGHT -> right = Math.max((int) mouseX + gripOffsetX, left + Math.max(minWidth, contentWidth));
            default -> { }
        }

        switch (verticalGrip) {
            case TOP -> top = Math.min((int) mouseY + gripOffsetY, bottom - minHeight);
            case BOTTOM -> bottom = Math.max((int) mouseY + gripOffsetY, top + minHeight);
            default -> { }
        }

        resizeTo(left, top, right - left, bottom - top);

        return true;
    }

    /// Releases the grip and reports the settled size to [#setSizeSink]. False when no grip was held.
    public boolean mouseReleased() {
        if (!resizing()) return false;

        if (horizontalGrip != Grip.NONE) {
            baseWidth = frame.getWidth();
            baseX = frame.getX();
        }

        horizontalGrip = Grip.NONE;
        verticalGrip = Grip.NONE;

        if (sizeSink != null) sizeSink.accept(baseWidth > 0 ? baseWidth : frame.getWidth(), frame.getHeight());

        return true;
    }

    /// Fits the frame's width to the widest of `rows` scrolled into `list`'s view, measured by `rowWidth`,
    /// plus `chromeWidth` for the frame's borders and scrollbar. Never narrower than the width the frame
    /// was opened or dragged to, and slides left rather than run off the viewport's right. Call from the
    /// frame's render, before drawing.
    public <E extends LayoutElement> void fitWidthToVisibleRows(
        AbstractScrollArea list,
        List<E> rows,
        ToIntFunction<E> rowWidth,
        int chromeWidth
    ) {
        if (resizing()) return;

        ScreenRectangle viewport = ModalManager.getInstance().getViewport();
        if (viewport == null) return;

        int widest = 0;

        for (E row : rows) {
            if (row.getY() + row.getHeight() > list.getY() && row.getY() < list.getBottom()) {
                widest = Math.max(widest, rowWidth.applyAsInt(row));
            }
        }

        if (baseWidth == 0) {
            baseWidth = frame.getWidth();
            baseX = frame.getX();
        }

        contentWidth = widest + chromeWidth;

        int width = Math.max(baseWidth, contentWidth);
        int x = Math.max(viewport.left(), Math.min(baseX, viewport.right() - width));

        if (width == frame.getWidth() && x == frame.getX()) return;

        // Relocating the list scrolls its selected row back into view, which would undo a scroll away from it.
        double scroll = list.scrollAmount();

        resizeTo(x, frame.getY(), width, frame.getHeight());
        list.setScrollAmount(scroll);
    }

    /// Cursor affordance for the grip: the arrow follows which axes the pointer (or the drag in flight)
    /// has hold of — a corner gets the omnidirectional one. Call from the frame's render.
    public void requestCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean horizontal = horizontalGrip != Grip.NONE;
        boolean vertical = verticalGrip != Grip.NONE;

        // Not dragging: probe the frame under the pointer without disturbing the live grips.
        if (!horizontal && !vertical) {
            boolean alongRows = mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
            boolean alongColumns = mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP;

            horizontal = alongRows
                && (inBandBefore(mouseX, frame.getX()) || inBandAfter(mouseX, frame.getRight()));
            vertical = alongColumns
                && (inBandBefore(mouseY, frame.getY()) || inBandAfter(mouseY, frame.getBottom()));
        }

        if (horizontal && vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        } else if (horizontal) {
            graphics.requestCursor(CursorTypes.RESIZE_EW);
        } else if (vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_NS);
        }
    }

    /// Whether the point is on the frame or on the [#GRIP] band around it — the frame's rect as
    /// [#mousePressed] sees it. A [FloatingModal] hit-tests with this rather than its own bounds,
    /// because the band lies outside them and would otherwise route to the screen behind.
    public boolean withinGripBounds(double mouseX, double mouseY) {
        return mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP
            && mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
    }

    private boolean resizing() {
        return horizontalGrip != Grip.NONE || verticalGrip != Grip.NONE;
    }

    /// Whether `p` is in the [#GRIP] band immediately **before** a left / top edge.
    private static boolean inBandBefore(double p, int edge) {
        return p >= edge - GRIP && p < edge;
    }

    /// Whether `p` is in the [#GRIP] band immediately **after** a right / bottom edge (both are
    /// exclusive bounds, so the band starts on the edge value itself).
    private static boolean inBandAfter(double p, int edge) {
        return p >= edge && p <= edge + GRIP;
    }

    /// Applies a new rect through the anchor units and re-lays out the frame's children against it. A
    /// centered frame keeps its offset from the viewport's center instead of its left / top edge.
    private void resizeTo(int x, int y, int width, int height) {
        ScreenRectangle viewport = ModalManager.getInstance().getViewport();
        if (viewport == null) return;

        anchored.setX1(AnchoredWidget.pixel(anchored.getHorizontalAnchorType() == AnchoredWidget.HorizontalAnchorType.CENTER_WIDTH
            ? x + width / 2 - viewport.getCenterInAxis(ScreenAxis.HORIZONTAL)
            : x - viewport.left()));
        anchored.setX2(AnchoredWidget.pixel(width));
        anchored.setY1(AnchoredWidget.pixel(anchored.getVerticalAnchorType() == AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT
            ? y + height / 2 - viewport.getCenterInAxis(ScreenAxis.VERTICAL)
            : y - viewport.top()));
        anchored.setY2(AnchoredWidget.pixel(height));

        anchored.relocate(viewport);
    }
}
