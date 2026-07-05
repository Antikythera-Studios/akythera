package com.yesman.akythera.core.util;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/// A utility enum that checks entities' and levels' logical side
public enum LogicalSide implements StringRepresentable {
    CLIENT(Level::isClientSide, entity -> entity.level().isClientSide(), "client"),
    SERVER(level -> !level.isClientSide(), entity -> !entity.level().isClientSide(), "server"),
    BOTH(level -> true, entity -> true, "both");

    final Predicate<Level> isLevelValidSide;
    final Predicate<Entity> isEntityValidSide;
    final String name;

    LogicalSide(Predicate<Level> isLevelValidSide, Predicate<Entity> isEntityValidSide, String name) {
        this.isLevelValidSide = isLevelValidSide;
        this.isEntityValidSide = isEntityValidSide;
        this.name = name;
    }

    /// Returns if the given level is on the right side
    public boolean isLevelOnValidSide(Level level) {
        return isLevelValidSide.test(level);
    }

    /// Returns if the given entity is on the right side
    public boolean isEntityOnValidSide(Entity entity) {
        return isEntityValidSide.test(entity);
    }

    public boolean validInClientSide() {
        return this == LogicalSide.BOTH || this == LogicalSide.CLIENT;
    }

    public boolean validInServerSide() {
        return this == LogicalSide.BOTH || this == LogicalSide.SERVER;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    public static final Codec<LogicalSide> CODEC = StringRepresentable.fromEnum(LogicalSide::values);
}
