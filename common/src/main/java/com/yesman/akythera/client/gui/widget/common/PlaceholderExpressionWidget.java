package com.yesman.akythera.client.gui.widget.common;

import org.jspecify.annotations.Nullable;

/// Widgets that have "placeholder" displaying function. Use
/// [#hasPlaceholderValue] to sear the text, background, or
/// outline color.
public interface PlaceholderExpressionWidget<T> extends DataBoundWidget<T> {
    /// Returns the value drawn while the widget holds none of its own.
    @Nullable T getPlaceholderValue();

    /// Installs the value of [#getPlaceholderValue]. Null takes the placeholder away.
    void setPlaceholderValue(@Nullable T value);

    /// Returns whether a placeholder is installed and the widget holds no value of its own. Where
    /// that state draws is the widget's own business.
    boolean hasPlaceholderValue();
}
