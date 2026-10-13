package com.yesman.akythera.client.api.gui.common;

import com.yesman.akythera.api.component.PatchComponent;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// A utility interface to ease the re-positioning widgets in proper place based on Anchor system.
///
/// Note: This interface supposes implementing any class that inherits [AbstractWidget] or [AbstractSelectionList].
///
/// Also a [PatchComponent], so a container can attach it by name. It is enabled while it is visible, which
/// also keeps it out of input ([AbstractWidget#isActive]).
public interface AnchoredWidget extends PatchComponent {
    /// Gets the first position parameter for the horizontal
    Unit getX1();

    /// Gets the second position parameter for the horizontal
    Unit getX2();

    /// Gets the first position parameter for the vertical
    Unit getY1();

    /// Gets the second position parameter for the vertical
    Unit getY2();

    /// Sets the first position parameter for the horizontal
    void setX1(Unit unit);

    /// Sets the second position parameter for the horizontal
    void setX2(Unit unit);

    /// Sets the second position parameter for the vertical
    void setY1(Unit unit);

    /// Sets the first position parameter for the horizontal
    void setY2(Unit unit);

    /// Gets the anchor type for the horizontal
    HorizontalAnchorType getHorizontalAnchorType();

    /// Gets the anchor type for the vertical
    VerticalAnchorType getVerticalAnchorType();

    /// Sets the anchor type for the horizontal
    void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType);

    /// Sets the anchor type for the vertical
    void setVerticalAnchorType(VerticalAnchorType verticalAnchorType);

    @Override
    default boolean isEnabled() {
        if (!(this instanceof AbstractWidget abstractWidget)) {
            throw new IllegalStateException("AnchoredWidget must implement AbstractWidget");
        }

        return abstractWidget.visible;
    }

    @Override
    default void setEnabled(final boolean enabled) {
        if (!(this instanceof AbstractWidget abstractWidget)) {
            throw new IllegalStateException("AnchoredWidget must implement AbstractWidget");
        }

        abstractWidget.visible = enabled;
    }

    /// Returns a basis rectangle to relocate child widgets
    default ScreenRectangle getContentRectangle() {
        if (!(this instanceof AbstractWidget abstractWidget)) {
            throw new IllegalStateException("AnchoredWidget must implement AbstractWidget");
        }

        return abstractWidget.getRectangle();
    }

    /// Relocate the widget with the given anchor types
    default void relocate(ScreenRectangle screenRectangle) {
        if (this instanceof AbstractWidget abstractWidget) {
            // Won't touch invalid widget
            if (getX1() == null || getX2() == null || getY1() == null || getY2() == null || getHorizontalAnchorType() == null || getVerticalAnchorType() == null) {
                return;
            }

            int x = getHorizontalAnchorType().positioner().startsAt(screenRectangle, this);
            int width = Math.max(1, getHorizontalAnchorType().scaler().withSize(screenRectangle, this));   // protect minimum size for the cases like creating a frame buffer
            int y = getVerticalAnchorType().positioner().startsAt(screenRectangle, this);
            int height = Math.max(1, getVerticalAnchorType().scaler().withSize(screenRectangle, this));    // protect minimum size for the cases like creating a frame buffer
            abstractWidget.setX(x);
            abstractWidget.setY(y);
            abstractWidget.setWidth(width);
            abstractWidget.setHeight(height);

            relocateChildren();
        } else {
            throw new IllegalStateException("AnchoredWidget must implement AbstractWidget");
        }
    }

    /// Lays the children out against this widget's rect, which [#relocate] has just set: the entries of a list,
    /// or each anchored child of a container against [#getContentRectangle].
    default void relocateChildren() {
        if (this instanceof AbstractSelectionList<?> list) {
            // effective calls `repositionEntries` to align entries according to the location change
            list.updateSizeAndPosition(list.getWidth(), list.getHeight(), list.getX(), list.getY());
        } else if (this instanceof AbstractContainerWidget abstractContainerWidget) {
            ScreenRectangle rectangle = getContentRectangle();

            // relocate children
            for (GuiEventListener e : abstractContainerWidget.children()) {
                if (e instanceof AnchoredWidget aw) aw.relocate(rectangle);
            }
        }
    }

    /// Backtrack the control variables from widget's current state
    default void backtrack(ScreenRectangle screenRectangle) {
        if (this instanceof AbstractWidget abstractWidget) {
            getHorizontalAnchorType().backTracker.backtrack(screenRectangle, this, abstractWidget);
            getVerticalAnchorType().backTracker.backtrack(screenRectangle, this, abstractWidget);

            if (this instanceof AbstractContainerWidget abstractContainerWidget) {
                ScreenRectangle widgetRectangle = getContentRectangle();

                // backtrack children
                for (GuiEventListener e : abstractContainerWidget.children()) {
                    if (e instanceof AbstractWidget abstractWidget1 && e instanceof AnchoredWidget anchoredWidget) {
                        anchoredWidget.getHorizontalAnchorType().backTracker.backtrack(widgetRectangle, anchoredWidget, abstractWidget1);
                        anchoredWidget.getVerticalAnchorType().backTracker.backtrack(widgetRectangle, anchoredWidget, abstractWidget1);
                    }
                }
            }
        } else {
            throw new IllegalStateException("AnchoredWidget must implement AbstractWidget");
        }
    }

    enum HorizontalAnchorType {
        /// Anchors the widget on the left side with a spacing by {@link #getX1()}, and has a width by {@link #getX2()} toward the right
        LEFT_WIDTH(
            (rect, widget) -> rect.left() + widget.getX1().pixels(rect, true), (rect, widget) -> widget.getX2().pixels(rect, true),
            (rect, widget, aw) -> {
                int px = aw.getX() - rect.left();
                int pw = aw.getWidth();
                widget.setX1(widget.getX1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.width())) : new Pixel(px));
                widget.setX2(widget.getX2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.width())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget in the center with a spacing by {@link #getX1()} (positive to right, negative to left), and has a width by {@link #getX2()} toward both left and right
        CENTER_WIDTH(
            (rect, widget) -> rect.getCenterInAxis(ScreenAxis.HORIZONTAL) + widget.getX1().pixels(rect, true) - widget.getX2().pixels(rect, true) / 2, (rect, widget) -> widget.getX2().pixels(rect, true),
            (rect, widget, aw) -> {
                int px = aw.getX() - rect.getCenterInAxis(ScreenAxis.HORIZONTAL) + aw.getWidth() / 2;
                int pw = aw.getWidth();
                widget.setX1(widget.getX1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.width())) : new Pixel(px));
                widget.setX2(widget.getX2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.width())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget on the right side with a spacing by {@link #getX1()}, and has a width by {@link #getX2()} toward the left
        RIGHT_WIDTH(
            (rect, widget) -> rect.right() - (widget.getX1().pixels(rect, true) + widget.getX2().pixels(rect, true)), (rect, widget) -> widget.getX2().pixels(rect, true),
            (rect, widget, aw) -> {
                int px = rect.right() - aw.getX() - aw.getWidth();
                int pw = aw.getWidth();
                widget.setX1(widget.getX1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.width())) : new Pixel(px));
                widget.setX2(widget.getX2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.width())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget on the left and right side with each {@link #getX1()} {@link #getX2()} spacing, which has a width by {@link #getX2()} - {@link #getX1()}
        LEFT_RIGHT(
            (rect, widget) -> rect.left() + widget.getX1().pixels(rect, true), (rect, widget) -> (rect.right() - widget.getX2().pixels(rect, true)) - (rect.left() + widget.getX1().pixels(rect, true)),
            (rect, widget, aw) -> {
                int px = aw.getX() - rect.left();
                int pw = rect.right() - aw.getX() - aw.getWidth();
                widget.setX1(widget.getX1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.width())) : new Pixel(px));
                widget.setX2(widget.getX2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.width())) : new Pixel(pw));
            }
        );

        final Positioner positioner;
        final Scaler scaler;
        final BackTracker backTracker;

        HorizontalAnchorType(Positioner positioner, Scaler scaler, BackTracker backTracker) {
            this.positioner = positioner;
            this.scaler = scaler;
            this.backTracker = backTracker;
        }

        public Positioner positioner() {
            return this.positioner;
        }

        public Scaler scaler() {
            return this.scaler;
        }
    }

    enum VerticalAnchorType {
        /// Anchors the widget on the top side with a spacing by {@link #getY1()}, and has a height by {@link #getY2()} toward the bottom
        TOP_HEIGHT(
            (rect, widget) -> rect.top() + widget.getY1().pixels(rect, false), (rect, widget) -> widget.getY2().pixels(rect, false),
            (rect, widget, aw) -> {
                int px = aw.getY() - rect.top();
                int pw = aw.getHeight();
                widget.setY1(widget.getY1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.height())) : new Pixel(px));
                widget.setY2(widget.getY2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.height())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget in the center with a spacing by {@link #getY1()} (positive to bottom, negative to top), and has a height by {@link #getY2()} toward both top and bottom
        CENTER_HEIGHT(
            (rect, widget) -> rect.getCenterInAxis(ScreenAxis.VERTICAL) + widget.getY1().pixels(rect, false) - widget.getY2().pixels(rect, false) / 2, (rect, widget) -> widget.getY2().pixels(rect, false),
            (rect, widget, aw) -> {
                int px = aw.getY() - rect.getCenterInAxis(ScreenAxis.VERTICAL) + aw.getHeight() / 2;
                int pw = aw.getHeight();
                widget.setY1(widget.getY1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.height())) : new Pixel(px));
                widget.setY2(widget.getY2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.height())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget on the bottom side with a spacing by {@link #getY1()}, and has a height by {@link #getY2()} toward the top
        BOTTOM_HEIGHT(
            (rect, widget) -> rect.bottom() - (widget.getY1().pixels(rect, false) + widget.getY2().pixels(rect, false)), (rect, widget) -> widget.getY2().pixels(rect, false),
            (rect, widget, aw) -> {
                int px = rect.bottom() - aw.getY() - aw.getHeight();
                int pw = aw.getHeight();
                widget.setY1(widget.getY1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.height())) : new Pixel(px));
                widget.setY2(widget.getY2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.height())) : new Pixel(pw));
            }
        ),

        /// Anchors the widget on the top and bottom side with each {@link #getY1()} {@link #getY2()} spacing, which has a height by {@link #getY2()} - {@link #getY1()}
        TOP_BOTTOM(
            (rect, widget) -> rect.top() + widget.getY1().pixels(rect, false), (rect, widget) -> (rect.bottom() - widget.getY2().pixels(rect, false)) - (rect.top() + widget.getY1().pixels(rect, false)),
            (rect, widget, aw) -> {
                int px = aw.getY() - rect.top();
                int pw = rect.bottom() - aw.getY() - aw.getHeight();
                widget.setY1(widget.getY1() instanceof Fraction ? new Fraction(Math.round(px * 100.0F / rect.height())) : new Pixel(px));
                widget.setY2(widget.getY2() instanceof Fraction ? new Fraction(Math.round(pw * 100.0F / rect.height())) : new Pixel(pw));
            }
        );

        final Positioner positioner;
        final Scaler scaler;
        final BackTracker backTracker;

        VerticalAnchorType(Positioner positioner, Scaler scaler, BackTracker backTracker) {
            this.positioner = positioner;
            this.scaler = scaler;
            this.backTracker = backTracker;
        }

        public Positioner positioner() {
            return this.positioner;
        }

        public Scaler scaler() {
            return this.scaler;
        }
    }

    /// Determines the widget's starting position, left for X and top for Y
    @FunctionalInterface
    interface Positioner {
        int startsAt(ScreenRectangle screenRectangle, AnchoredWidget anchoredWidget);
    }

    /// Determines the widget's width and height
    @FunctionalInterface
    interface Scaler {
        int withSize(ScreenRectangle screenRectangle, AnchoredWidget anchoredWidget);
    }

    /// Re-calculate the widget x1, x2, y1, and y2 based on the widget position fields
    @FunctionalInterface
    interface BackTracker {
        // anchoredWidget == abstractWidget
        void backtrack(ScreenRectangle screenRectangle, AnchoredWidget anchoredWidget, AbstractWidget abstractWidget);
    }

    /// Uses an absolute pixel unit
    static Pixel pixel(final int value) { return new Pixel(value); }

    /// Uses an 'n%' unit from a parent screen rectangle width
    ///
    /// ```If screen rectangle is 1920x1080 and value is 30, the pixels are 576 for width, 324 for height```
    static Fraction fraction(final int value) { return new Fraction(value); }

    interface Unit {
        /// @param xy true = width, false = height.
        int pixels(ScreenRectangle screenRectangle, boolean xy);
    }

    record Pixel(int value) implements Unit {
        @Override
        public int pixels(ScreenRectangle screenRectangle, boolean xy) {
            return value;
        }
    }

    record Fraction(int value) implements Unit {
        @Override
        public int pixels(ScreenRectangle screenRectangle, boolean xy) {
            return Math.round((xy ? screenRectangle.width() : screenRectangle.height()) * value * 0.01F);
        }
    }

    /// For the common anchor implementation, you can copy and paste this code
    /// at the bottom of the class
    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    /*
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
    }
    */
}
