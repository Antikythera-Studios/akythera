package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

/// What an [AkytheraWidget] built from a blueprint runs.
///
/// The input methods are asked for what the widgets inside leave unhandled, and answer whether the script handled
/// it.
public interface WidgetScript extends WidgetContainerScript {
    /// Called once the container is built, its widgets attached.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void construct() {
    }

    /// Called every client tick.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void tick() {
    }

    /// Called when the container is laid out at a new size, in GUI pixels.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void onResized(final int width, final int height) {
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    default void mouseMoved(final double mouseX, final double mouseY) {
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean mouseReleased(final MouseButtonEvent event) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean mouseDragged(final MouseButtonEvent event, final double dragX, final double dragY) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean keyPressed(final KeyEvent event) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean keyReleased(final KeyEvent event) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean charTyped(final CharacterEvent event) {
        return false;
    }

    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean preeditUpdated(final PreeditEvent event) {
        return false;
    }
}
