package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// Which running placements the conditions of a [TreeNode] stop.
public enum AbortMode implements StringRepresentable {
    NONE("none"), // tested only when the placement starts
    SELF("self"), // cancels the placement once a condition turns false
    LOWER_PRIORITY("lower_priority"), // cancels a later branch of the same selector once every condition turns true
    BOTH("both");

    public static final Codec<AbortMode> CODEC = StringRepresentable.fromEnum(AbortMode::values);

    private final String name;

    AbortMode(final String name) {
        this.name = name;
    }

    public boolean abortsSelf() {
        return this == SELF || this == BOTH;
    }

    public boolean abortsLowerPriority() {
        return this == LOWER_PRIORITY || this == BOTH;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }
}
