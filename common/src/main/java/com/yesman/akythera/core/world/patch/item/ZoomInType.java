package com.yesman.akythera.core.world.patch.item;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum ZoomInType implements StringRepresentable {
    NONE("none"),
    ALWAYS("always"),
    USE_TICK("use_tick"),
    AIMING("none"),
    CUSTOM("none")
    ;

    final String name;

    ZoomInType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }
}