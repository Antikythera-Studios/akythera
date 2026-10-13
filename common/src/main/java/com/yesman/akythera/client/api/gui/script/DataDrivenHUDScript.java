package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import net.minecraft.core.Holder;

/// Runs the [HUDScript] events of a [DataDrivenWidgetBlueprint].
public final class DataDrivenHUDScript extends DataDrivenWidgetContainerScript implements HUDScript {
    public DataDrivenHUDScript(final Holder.Reference<DataDrivenWidgetBlueprint> blueprint, final AkytheraHUD hud) {
        super(blueprint, hud, HUDScript.class);
    }

    @Override
    public void onAdded() {
        executor.invokeMethod("onAdded", null);
    }

    @Override
    public void onRemoved() {
        executor.invokeMethod("onRemoved", null);
    }

    @Override
    public void onUpdated() {
        executor.invokeMethod("onUpdated", null);
    }
}
