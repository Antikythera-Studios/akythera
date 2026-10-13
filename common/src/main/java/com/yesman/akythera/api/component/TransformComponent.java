package com.yesman.akythera.api.component;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.math.Transform;
import org.jspecify.annotations.Nullable;

/// A component that occupies a space: it sits at [#localTransform] inside its parent's space, taking on the parent
/// elements its [#transformInheritPolicy] lists, and whatever is attached under it lives in its own. A component that
/// is no `TransformComponent` places nothing; its children live in the space of its nearest `TransformComponent`
/// ancestor.
///
/// The parent space is found by walking [#host]:
/// - a `TransformComponent` gives its [#modelTransform],
/// - a [DynamicPatchComponent] passes the walk on to its owner,
/// - an [ItemPatch] an entity holds gives the [EquipmentSlotAnchor] drawing its slot,
/// - any other host (an [EntityPatch], the shared [ItemPatch]) is the model space's origin.
public interface TransformComponent extends PatchComponent {
    /// This component's transform inside its parent space.
    Transform localTransform();

    /// Replaces [#localTransform] on this instance.
    ///
    /// @throws UnsupportedOperationException always, unless the component overrides this
    default void setLocalTransform(final Transform transform) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " holds no transform of its own");
    }

    /// The host this component is attached under, null for one attached nowhere.
    @Nullable ComponentAttachable host();

    /// The elements of the parent space [#modelTransform] takes on.
    TransformInheritPolicy transformInheritPolicy();

    /// The space [#localTransform] is expressed in, in the patch's model space.
    default Transform parentModelTransform() {
        return spaceOf(host(), false);
    }

    /// [#parentModelTransform] one server tick back.
    default Transform previousParentModelTransform() {
        return spaceOf(host(), true);
    }

    /// This component's transform in the patch's model space, the armature root's space.
    default Transform modelTransform() {
        return transformInheritPolicy().inherit(parentModelTransform(), localTransform());
    }

    /// [#modelTransform] one server tick back, the far end of a sweep.
    default Transform previousModelTransform() {
        return transformInheritPolicy().inherit(previousParentModelTransform(), localTransform());
    }

    /// This component's transform in world space, or null when the walk reaches no entity.
    default @Nullable Transform worldTransform() {
        EntityPatch<?> entity = entityOf(host());

        return entity == null ? null : entity.getModelTransform().mul(modelTransform());
    }

    /// The space `host` gives the components attached under it, in the patch's model space.
    static Transform spaceOf(final @Nullable ComponentAttachable host, final boolean previous) {
        Object walked = host;

        while (walked != null) {
            switch (walked) {
                case TransformComponent transform -> {
                    return previous ? transform.previousModelTransform() : transform.modelTransform();
                }
                case DynamicPatchComponent<?> dynamic -> walked = dynamic.getOwner();
                case ItemPatch itemPatch -> {
                    EquipmentSlotAnchor anchor = itemPatch.getAnchor();

                    if (anchor == null) return Transform.IDENTITY;

                    return previous ? anchor.previousModelTransform() : anchor.modelTransform();
                }
                default -> walked = null;
            }
        }

        return Transform.IDENTITY;
    }

    /// The entity whose model space `host` lives in, or null when it lives in none.
    static @Nullable EntityPatch<?> entityOf(final @Nullable ComponentAttachable host) {
        Object walked = host;

        while (walked != null) {
            switch (walked) {
                case EntityPatch<?> entity -> {
                    return entity;
                }
                case TransformComponent transform -> walked = transform.host();
                case DynamicPatchComponent<?> dynamic -> walked = dynamic.getOwner();
                case ItemPatch itemPatch -> walked = itemPatch.getHolder();
                default -> walked = null;
            }
        }

        return null;
    }
}
