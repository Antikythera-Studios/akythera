package com.yesman.akythera.api.component;

import net.minecraft.nbt.CompoundTag;

/// A component that saves data on disk
public interface SerializableDynamicComponent<T> extends DynamicPatchComponent<T> {
    /// Save component instance state into entity data
    void writeData(final CompoundTag compound);

    /// Read from the entity data to restore the last component instance state
    void readData(final CompoundTag compound);
}
