package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.Holder;

/// Runs the [WidgetScript] events of a [DataDrivenWidgetBlueprint]. An input method the blueprint leaves
/// unimplemented handles nothing.
public class DataDrivenWidgetScript extends DataDrivenWidgetContainerScript implements WidgetScript {
    public DataDrivenWidgetScript(final Holder.Reference<DataDrivenWidgetBlueprint> blueprint, final AkytheraWidget widget) {
        super(blueprint, widget, WidgetScript.class);
    }

    protected DataDrivenWidgetScript(
        final Holder.Reference<DataDrivenWidgetBlueprint> blueprint,
        final ComponentAttachable container,
        final Class<? extends WidgetScript> layoutProvider
    ) {
        super(blueprint, container, layoutProvider);
    }

    @Override
    public void construct() {
        executor.invokeMethod("construct", null);
    }

    @Override
    public void tick() {
        executor.invokeMethod("tick", null);
    }

    @Override
    public void onResized(final int width, final int height) {
        executor.invokeMethod("onResized", null, width, height);
    }

    @Override
    public void mouseMoved(final double mouseX, final double mouseY) {
        executor.invokeMethod("mouseMoved", null, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        return Boolean.TRUE.equals(executor.invokeMethod("mouseClicked", () -> false, event, doubleClick));
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        return Boolean.TRUE.equals(executor.invokeMethod("mouseReleased", () -> false, event));
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double dragX, final double dragY) {
        return Boolean.TRUE.equals(executor.invokeMethod("mouseDragged", () -> false, event, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return Boolean.TRUE.equals(executor.invokeMethod("mouseScrolled", () -> false, mouseX, mouseY, scrollX, scrollY));
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        return Boolean.TRUE.equals(executor.invokeMethod("keyPressed", () -> false, event));
    }

    @Override
    public boolean keyReleased(final KeyEvent event) {
        return Boolean.TRUE.equals(executor.invokeMethod("keyReleased", () -> false, event));
    }

    @Override
    public boolean charTyped(final CharacterEvent event) {
        return Boolean.TRUE.equals(executor.invokeMethod("charTyped", () -> false, event));
    }

    @Override
    public boolean preeditUpdated(final PreeditEvent event) {
        return Boolean.TRUE.equals(executor.invokeMethod("preeditUpdated", () -> false, event));
    }
}
