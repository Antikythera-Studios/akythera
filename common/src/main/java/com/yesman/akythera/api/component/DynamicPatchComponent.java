package com.yesman.akythera.api.component;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import org.jspecify.annotations.Nullable;

/// A [PatchComponent] that is produced per-instance and keeps access to its owner `T`.
public interface DynamicPatchComponent<T> extends PatchComponent {
    /// Returns the instance this component is bound to (the patch or game object that owns it).
    T getOwner();

    /// Refresh the component with new owner.
    ///
    /// Can keep the old data in renewed component, or completely remove the
    /// old context and fill with initial values. Called on copy constructor
    /// of component owner.
    ///
    /// @see EntityPatch#(EntityPatch)
    DynamicPatchComponent<T> getRenewed(final T newOwner);

    /// Walks up the owner chain via [#getOwner()] and returns the first owner assignable to
    /// `type`, or `null` if none matches. The terminal owner (the non-[DynamicPatchComponent]
    /// at the top of the chain, e.g. the raw entity) is tested too.
    @SuppressWarnings("unchecked")
    default <P> @Nullable P findParentComponent(Class<P> type) {
         Object o = getOwner();

         while (o instanceof DynamicPatchComponent<?> dynamicComponent) {
             if (type.isAssignableFrom(o.getClass())) return (P)o;
             o = dynamicComponent.getOwner();
         }

         if (type.isAssignableFrom(o.getClass())) return (P)o;

         return null;
    }
}
