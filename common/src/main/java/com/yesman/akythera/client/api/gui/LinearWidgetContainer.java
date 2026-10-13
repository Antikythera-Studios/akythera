package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// Lays its visible children out one after another along [#axis], in attachment order. Each child gets a
/// cell as long as its own size along the axis and as wide as the container across it, and is anchored inside
/// that cell.
///
/// A child's size along the axis must be a pixel size: the container sizes itself from its children, so a
/// fraction of the container would size the container from itself. The container's length along the axis is
/// the sum of its children's plus [#padding] between and around them, so its own X2 (horizontal) or Y2
/// (vertical) unit is ignored, and a stretching anchor on that axis (`LEFT_RIGHT`, `TOP_BOTTOM`) places it like
/// `LEFT_WIDTH` / `TOP_HEIGHT`.
public abstract class LinearWidgetContainer extends AkytheraWidget {
    private final ScreenAxis axis;

    /// Space between the children and around them along the axis, in pixels.
    private int padding;

    protected LinearWidgetContainer(
        final ScreenAxis axis,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final int padding
    ) {
        super(x1, x2, y1, y2, horizontalAnchor, verticalAnchor);

        this.axis = axis;
        this.padding = padding;
    }

    public int getPadding() {
        return padding;
    }

    public void setPadding(final int padding) {
        this.padding = padding;
        relocateChildren();
    }

    /// The length `child` takes along the axis: its anchored size against `basis`, or its current size when it
    /// is not anchored.
    private int lengthOf(final AbstractWidget child, final ScreenRectangle basis) {
        if (!(child instanceof AnchoredWidget anchored)) {
            return axis == ScreenAxis.HORIZONTAL ? child.getWidth() : child.getHeight();
        }

        return axis == ScreenAxis.HORIZONTAL
            ? anchored.getHorizontalAnchorType().scaler().withSize(basis, anchored)
            : anchored.getVerticalAnchorType().scaler().withSize(basis, anchored);
    }

    @Override
    public void relocate(final ScreenRectangle screenRectangle) {
        int length = padding;

        for (AbstractWidget child : children()) {
            if (child.visible) length += lengthOf(child, screenRectangle) + padding;
        }

        length = Math.max(1, length);

        if (axis == ScreenAxis.HORIZONTAL) {
            int offset = getX1().pixels(screenRectangle, true);
            int x = switch (getHorizontalAnchorType()) {
                case RIGHT_WIDTH -> screenRectangle.right() - offset - length;
                case CENTER_WIDTH -> screenRectangle.getCenterInAxis(ScreenAxis.HORIZONTAL) + offset - length / 2;
                case LEFT_WIDTH, LEFT_RIGHT -> screenRectangle.left() + offset;
            };

            setRectangle(
                length,
                Math.max(1, getVerticalAnchorType().scaler().withSize(screenRectangle, this)),
                x,
                getVerticalAnchorType().positioner().startsAt(screenRectangle, this)
            );
        } else {
            int offset = getY1().pixels(screenRectangle, false);
            int y = switch (getVerticalAnchorType()) {
                case BOTTOM_HEIGHT -> screenRectangle.bottom() - offset - length;
                case CENTER_HEIGHT -> screenRectangle.getCenterInAxis(ScreenAxis.VERTICAL) + offset - length / 2;
                case TOP_HEIGHT, TOP_BOTTOM -> screenRectangle.top() + offset;
            };

            setRectangle(
                Math.max(1, getHorizontalAnchorType().scaler().withSize(screenRectangle, this)),
                length,
                getHorizontalAnchorType().positioner().startsAt(screenRectangle, this),
                y
            );
        }

        relocateChildren();
    }

    @Override
    public void relocateChildren() {
        ScreenRectangle rectangle = getRectangle();
        int cursor = (axis == ScreenAxis.HORIZONTAL ? getX() : getY()) + padding;

        for (AbstractWidget child : children()) {
            if (!child.visible) continue;

            int length = lengthOf(child, rectangle);
            ScreenRectangle cell = axis == ScreenAxis.HORIZONTAL
                ? new ScreenRectangle(cursor, getY(), length, getHeight())
                : new ScreenRectangle(getX(), cursor, getWidth(), length);

            placeChild(child, cell);

            cursor += length + padding;
        }
    }
}
