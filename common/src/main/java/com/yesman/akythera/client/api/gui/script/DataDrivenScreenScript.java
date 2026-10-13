package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import net.minecraft.core.Holder;

/// Runs the [ScreenScript] events of a [DataDrivenWidgetBlueprint].
public final class DataDrivenScreenScript extends DataDrivenWidgetScript implements ScreenScript {
    public DataDrivenScreenScript(final Holder.Reference<DataDrivenWidgetBlueprint> blueprint, final AkytheraScreen screen) {
        super(blueprint, screen, ScreenScript.class);
    }

    @Override
    public void onOpened() {
        executor.invokeMethod("onOpened", null);
    }

    @Override
    public void onClosed() {
        executor.invokeMethod("onClosed", null);
    }
}
