package com.yesman.akythera.client.api.gui.common;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;

/// A UI Interface for scrollable components
/// The feature is inspired by scroll bar in [AbstractSelectionList]
/// This will be helpful for designing scrolling bar into widget, providing
/// an automated scroll bar height calculation and position
///
/// Note: this must implement a class that inherits [AbstractWidget]
public interface ScrollableWidget {
    /// Returns the maximum height of the widget
    int getMaxWidgetHeight();

    /// Returns how far the scroller will move within a step
    int getScrollStride();


    /// Returns the width of a scroller
    default int getScrollWidth() {
        return 6;
    }

    ///Computes the visual height of the scroll
    default int getScrollHeight() {
        AbstractWidget asWidget = this.asWidget();

        return Math.round(asWidget.getHeight() / (float)this.getMaxWidgetHeight() * asWidget.getHeight());
    }

    /// Returns if the scroller is appeared
    /// Scroller is only visible when the maximum height is longer than the visual height
    default boolean isScrollVisible() {
        return this.getMaxWidgetHeight() > this.asWidget().getHeight();
    }

    /// Moves the scroll in specific direction
    default void moveScroll(int direction) {
        int nextScrollPosition = this.getScrollPosition() + direction * this.getScrollStride();
        this.setScrollPosition(Math.clamp(nextScrollPosition, 0, this.asWidget().getHeight() - this.getScrollHeight()));
    }

    /// Returns the vertical position of the scroll
    /// The position will vary depending on [#getScrollStride()]
    /// the value must be clamped between 0 and [AbstractWidget#getHeight()] - [#getScrollHeight()]
    int getScrollPosition();

    /// Sets the scroll position to the given parameter
    /// Implementation would be a simple setter, as this method is only meant to be used in [#moveScroll]
    void setScrollPosition(int position);

    /// Computes scroll position parameters and render
    /// Developers should call this method instead of [#renderScroll] to get fully resolved position parameters
    default void computeScrollPositionAndRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isScrollVisible()) return;

        AbstractWidget asWidget = this.asWidget();
        int right = asWidget.getX() + asWidget.getWidth() - 1;
        int top = asWidget.getY() + this.getScrollPosition();

        this.renderScroll(guiGraphics, mouseX, mouseY, partialTick, right - this.getScrollWidth(), top, right, top + this.getScrollHeight());
    }

    /// Renders the scroll with fully resolved location parameters
    /// Developers could implement, but can't call this method directly
    void renderScroll(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int minX, int minY, int maxX, int maxY);

    /// Apply a scissor effect and move the widget components in y direction before render contents of the widget
    default void preRenderProcess(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.pose().pushMatrix();
        AbstractWidget asWidget = this.asWidget();
        int yTranslate = Math.round(this.getScrollPosition() / (float)asWidget.getHeight() * this.getMaxWidgetHeight());
        guiGraphics.pose().translate(0, -yTranslate);
        guiGraphics.enableScissor(asWidget.getX(), asWidget.getY(), asWidget.getX() + asWidget.getWidth(), asWidget.getY() + asWidget.getHeight());
    }

    /// Remove translations and disable scissor
    default void postRenderProcess(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.disableScissor();
        guiGraphics.pose().popMatrix();
    }

    /// Cast to [AbstractWidget]. it will throw an exception if implementation doesn't inherit it
    default AbstractWidget asWidget() {
        if (this instanceof AbstractWidget abstractWidget) return abstractWidget;
        else throw new IllegalStateException("ScrollableWidget must implement AbstractWidget");
    }
}
