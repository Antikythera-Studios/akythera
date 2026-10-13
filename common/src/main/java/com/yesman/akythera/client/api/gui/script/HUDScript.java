package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.HudManager;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.reflection.BlueprintFunction;

/// What an [AkytheraHUD] built from a blueprint runs.
public interface HUDScript extends WidgetContainerScript {
    /// Called when the [HudManager] starts showing the HUD.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onAdded() {
    }

    /// Called when the [HudManager] stops showing the HUD.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onRemoved() {
    }

    /// Called every client tick while the HUD is shown and enabled.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onUpdated() {
    }
}
