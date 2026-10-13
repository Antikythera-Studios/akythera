package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/// What a [WidgetBlueprint] builds.
public enum WidgetUsage implements StringRepresentable {
    /// An [AkytheraScreen], opened over the game.
    SCREEN,
    /// An [AkytheraWidget], placed in another blueprint.
    WIDGET,
    /// An [AkytheraHUD], drawn with the vanilla HUD. Holds only widgets that take no input.
    HUD;

    public static final Codec<WidgetUsage> CODEC = StringRepresentable.fromEnum(WidgetUsage::values);

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
