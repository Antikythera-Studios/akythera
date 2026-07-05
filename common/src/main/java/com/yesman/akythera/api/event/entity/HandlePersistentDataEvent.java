package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.nbt.CompoundTag;

/// Events when read & write permanent data of entity, the data is transformed to [CompoundTag]
public abstract class HandlePersistentDataEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final CompoundTag compoundTag;

    public HandlePersistentDataEvent(final EntityPatch<?> entityPatch, final CompoundTag compoundTag) {
        super(entityPatch);

        this.compoundTag = compoundTag;
    }

    public final CompoundTag getCompoundTag() {
        return compoundTag;
    }

    /// Called on writing data to save
    public static final class Serialize extends HandlePersistentDataEvent {
        public Serialize(EntityPatch<?> entityPatch, CompoundTag compoundTag) {
            super(entityPatch, compoundTag);
        }
    }

    /// Called on reading data that were saved in a world
    public static final class Deserialize extends HandlePersistentDataEvent {
        public Deserialize(EntityPatch<?> entityPatch, CompoundTag compoundTag) {
            super(entityPatch, compoundTag);
        }
    }
}
