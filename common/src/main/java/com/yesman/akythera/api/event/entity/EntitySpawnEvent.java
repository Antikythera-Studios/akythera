package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/// An event triggered when an entity is spawned in a world
public final class EntitySpawnEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final Level level;
    private final boolean worldGenSpawn;

    /// @param worldGenSpawn whether this spawn is caused by a world loading and always false in client level.
    ///
    /// Refers to [PersistentEntitySectionManager#addEntity(EntityAccess, boolean)]
    public EntitySpawnEvent(final EntityPatch<?> entityPatch, final Level level, final boolean worldGenSpawn) {
        super(entityPatch);
        this.level = level;
        this.worldGenSpawn = worldGenSpawn;
    }

    public Level getLevel() {
        return level;
    }

    /// Returns whether the entity is created from saved data
    public boolean isWorldGenSpawn() {
        return worldGenSpawn;
    }
}
