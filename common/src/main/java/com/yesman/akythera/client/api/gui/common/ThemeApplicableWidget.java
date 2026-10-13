package com.yesman.akythera.client.api.gui.common;

import org.jspecify.annotations.Nullable;

/// A utility interface for widgets that have various styles based on {@link WidgetTheme}
public interface ThemeApplicableWidget<T extends WidgetTheme> {
    /// Returns if the given theme enum is supported by this widget
    boolean isSupportedTheme(WidgetTheme theme);

    /// Returns the current theme of the widget, or null when it draws without one
    @Nullable
    T getTheme();

    /// Applies a new theme
    void setTheme(@Nullable T theme);
}
