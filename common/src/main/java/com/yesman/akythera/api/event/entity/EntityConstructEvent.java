package com.yesman.akythera.api.event.entity;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// An event triggered when an entity patch is just constructed.
///
/// Key differences vs [EntitySpawnEvent]:
/// [EntityConstructEvent] called when [EntityPatch] is just instantiated,
/// while [EntitySpawnEvent] is called when an entity placed to a world. This
/// includes a case when an entity changes dimension from overworld to nether.
public final class EntityConstructEvent extends EntityPatchEvent<EntityPatch<?>> {
    private final Map<String, PatchComponent> componentsToAttach;

    /// Whether the event is called by copy constructor. [#componentsToAttach] is already filled by previous entity patch.
    private final boolean copied;

    public <T extends Entity> EntityConstructEvent(
        final EntityPatch<T> entityPatch,
        final Map<String, PatchComponent> componentsToAttach,
        final boolean copied
    ) {
        super(entityPatch);

        this.componentsToAttach = componentsToAttach;
        this.copied = copied;
    }

    public void addComponent(final String componentName, final PatchComponent patchComponent) {
        componentsToAttach.put(componentName, patchComponent);
    }

    @SuppressWarnings("unchecked")
    public <T extends PatchComponent> @Nullable T getComponent(final String componentName) {
        return (T)componentsToAttach.get(componentName);
    }

    /// True when called by [EntityPatch]'s copy constructor.
    public boolean isCopied() {
        return copied;
    }
}
