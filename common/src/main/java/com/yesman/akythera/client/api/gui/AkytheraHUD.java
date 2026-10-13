package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.script.HUDScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/// A widget container drawn over the game with the vanilla HUD, filling the GUI-scaled window. It takes no
/// input. Shown through [HudManager].
@ScriptType
public class AkytheraHUD extends AkytheraWidget {
    /// Builds the HUD `blueprint` describes for `owner`, the local player's patch.
    public AkytheraHUD(final Holder.Reference<WidgetBlueprint> blueprint, final @Nullable Object owner) {
        super(blueprint, owner);
    }

    public AkytheraHUD() {
        super(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.TOP_BOTTOM
        );
    }

    /// Ticks the widgets, then tells a [HUDScript].
    @Override
    public void tick() {
        super.tick();

        if (getScript() instanceof HUDScript hudScript) hudScript.onUpdated();
    }
}
