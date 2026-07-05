package com.yesman.akythera.mixinaccessor.common;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import org.jetbrains.annotations.ApiStatus;

public interface EntityAccessor {
    EntityPatch<?> akythera$getEntityPatch();

    /// Reload the entity patch. Only used for special case (AkytheraLabs reloading button)
    /// and shouldn't be called by third-party mod.
    @ApiStatus.Internal
    void akythera$setEntityPatch(final EntityPatch<?> dynamicObj);
}
