package com.yesman.akythera.core.blueprint.widget;

import com.yesman.akythera.core.blueprint.ComponentBlueprint;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// What the container a [WidgetBlueprint] builds runs: the widgets it attaches, and the graphs or Java code that
/// react to them.
public interface WidgetContainerScript {
    /// Entries of (name, widget) the container attaches before it is constructed. A source code script may leave
    /// it empty and attach its widgets itself.
    default Map<String, ComponentBlueprint<?, ?>> componentsToAttach() {
        return Map.of();
    }

    /// What runs when the widget event `key` (`<path>.<event>`) fires, taking its arguments in the order of
    /// `argumentNames`. Null when nothing does.
    default @Nullable Consumer<Object[]> widgetEvent(final String key, final List<String> argumentNames) {
        return null;
    }

    /// What answers the widget binding `key` (`<path>.<binding>`) each time it is read. Null when nothing does.
    default @Nullable Supplier<@Nullable Object> widgetBinding(final String key) {
        return null;
    }
}
