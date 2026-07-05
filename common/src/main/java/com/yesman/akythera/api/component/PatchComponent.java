package com.yesman.akythera.api.component;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/// A decoupled functionality module that can be attached to any patch
/// Instead of creating subclasses of [EntityPatch], developers can add
/// a new functionality to exising entity patches. This is useful for widely
/// handled entities like player.
///
/// If you prefer a dedicated patch class, register them by [EntityType] as key
/// through [ComponentsFactory#registerEntityPatchProvider]. But this method
/// has a potential danger to overwrite existing provider.
public interface PatchComponent<T> {
    T getOwner();

    @SuppressWarnings("unchecked")
    default <P> @Nullable P findParentComponent(Class<P> type) {
         Object o = getOwner();

         while (o instanceof PatchComponent<?> patchComponent) {
             if (type.isAssignableFrom(o.getClass())) return (P)o;
             o = patchComponent.getOwner();
         }

         if (type.isAssignableFrom(o.getClass())) return (P)o;
         return null;
    }
}
