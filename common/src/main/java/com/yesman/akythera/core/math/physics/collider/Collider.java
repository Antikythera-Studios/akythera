package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/// The authored shape of a collider, and the blueprint of the [ColliderComponent] that carries it
/// at a transform. Compatible with Minecraft's collider, [AABB].
///
/// Attachable under anything that hosts components — a [BoneSocket], an [EntityPatch], anything —
/// which is why the host is taken as a plain [ComponentAttachable] and never read: a collider is
/// placed by whoever queries it, not by what it hangs off.
///
/// Beware colliders are not directly used to check collision — [ColliderComponent] is.
public abstract class Collider implements ComponentBlueprint<ColliderComponent<?>, ComponentAttachable> {
    /// Shape dispatch. Keyed `shape`, not `type`: the component blueprint registry already spends
    /// `type` on the component id (`akythera:collider`) in the very same object.
    public static final MapCodec<Collider> MAP_CODEC =
        ColliderType.ENUM_MANAGER.getNameCodec()
            .dispatchMap("shape", Collider::type, ColliderType::mapCodec);

    /// Transform of a pivot point
    ///
    /// Kept the transform info for [MapCodec], while most collider
    /// components are already applied by pivot transform.
    private final Transform pivotTransform;

    public Collider(final Transform pivotTransform) {
        this.pivotTransform = pivotTransform;
    }

    /// Builds this shape's component. `host` is unread — it exists only so any component host can
    /// attach a collider; the component starts at the identity transform and is placed by whoever
    /// runs the collision query, through [ColliderComponent#applyTransform]. Null for a collider that
    /// hangs off nothing, which is what a sweep builds.
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
}
