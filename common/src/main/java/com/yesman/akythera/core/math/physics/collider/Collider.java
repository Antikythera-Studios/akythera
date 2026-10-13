package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/// The authored shape of a collider, and the blueprint of the [ColliderComponent] that carries it
/// at a transform. Compatible with Minecraft's collider, [AABB].
///
/// Attachable under anything that hosts components — a [TransformSocket], an [EntityPatch], anything. The
/// component keeps its host for [ColliderComponent#parentModelTransform], and is placed by whoever queries it.
///
/// Beware colliders are not directly used to check collision — [ColliderComponent] is.
public abstract class Collider implements ComponentBlueprint<ColliderComponent<?>, ComponentAttachable> {
    /// Shape dispatch. Keyed `shape`, not `type`: the component blueprint registry already spends
    /// `type` on the component id (`akythera:collider`) in the very same object.
    public static final MapCodec<Collider> MAP_CODEC =
        ColliderType.ENUM_MANAGER.getNameCodec().dispatchMap("shape", Collider::type, ColliderType::mapCodec);

    /// Transform of a pivot point
    ///
    /// Kept the transform info for [MapCodec], while most collider
    /// components are already applied by pivot transform.
    private final Transform pivotTransform;

    /// The elements of the space the pivot is placed in that each built [ColliderComponent] takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Whether each built [ColliderComponent] starts switched on.
    private final boolean enabled;

    public Collider(final Transform pivotTransform, final TransformInheritPolicy transformInheritPolicy, final boolean enabled) {
        this.pivotTransform = pivotTransform;
        this.transformInheritPolicy = transformInheritPolicy;
        this.enabled = enabled;
    }

    /// Builds this shape's component under `host`. The component starts at the identity transform and is placed by
    /// whoever runs the collision query, through [ColliderComponent#applyTransform]. Null for a collider that hangs
    /// off nothing, which is what a sweep builds.
    @Override
    public abstract ColliderComponent<?> instantiate(final @Nullable ComponentAttachable host);

    public abstract ColliderType type();

    /// Every shape rides the one dispatching codec, so `akythera:collider` is a single registered
    /// component type with [#type] choosing the shape inside it.
    @Override
    public final MapCodec<Collider> mapCodec() {
        return MAP_CODEC;
    }

    public Transform getPivotTransform() {
        return pivotTransform;
    }

    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }
}
