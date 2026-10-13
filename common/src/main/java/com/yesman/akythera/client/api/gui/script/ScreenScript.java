package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.core.reflection.BlueprintFunction;

/// What an [AkytheraScreen] built from a blueprint runs. [WidgetScript#onResized] is called when the window is
/// resized while the screen is open.
public interface ScreenScript extends WidgetScript {
    /// Called when the screen is opened.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onOpened() {
    }

    /// Called when the screen is closed or replaced.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onClosed() {
    }
}
