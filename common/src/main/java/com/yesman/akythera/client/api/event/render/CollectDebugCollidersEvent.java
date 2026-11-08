package com.yesman.akythera.client.api.event.render;

import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;

import java.util.List;

/// Fired while [ColliderDebugRenderer] collects the colliders of a patch to draw in the hitbox scene, so a listener
/// can add colliders the patch does not hold.
public final class CollectDebugCollidersEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final List<ColliderDebugRenderer.ColliderSubmit> submits;

    public CollectDebugCollidersEvent(final EntityPatch<?> entityPatch, final List<ColliderDebugRenderer.ColliderSubmit> submits) {
        super(entityPatch);
        this.submits = submits;
    }

    /// Adds a collider to draw, its transform in armature space.
    public void add(final ColliderDebugRenderer.ColliderSubmit submit) {
        submits.add(submit);
    }
}
