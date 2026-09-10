package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.GjkIntersection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// A [Collider] at a transform — the only form collision is ever checked against
/// ([CollisionDetectionLogics#overlap]).
///
/// Knows nothing about what holds it: no owner, no host chain, no ticking. Where a collider sits is
/// [#applyTransform]'s argument, decided by whatever runs the query — a hit sweep off an armature
/// bone, a projectile's own position, a raw [AABB].
///
/// Subclasses call [#applyTransform] at the end of their own constructor: their vertex and normal
/// arrays are instance field initializers, still null while `super()` runs.
public abstract class ColliderComponent<T extends Collider> implements PatchComponent {
    /// Holding collider
    private final T collider;

    /// An [AABB] that wraps the space where the collider can exist
    protected AABB outerAABB;

    protected ColliderComponent(final T collider) {
        this.collider = collider;
    }

    public final T getCollider() {
        return collider;
    }

    public final ColliderType type() {
        return collider.type();
    }

    /// Apply the transform to instance
    public abstract void applyTransform(final Transform transform);

    /// [#applyTransform], handing back this component—for building a placed collider in one
    /// expression.
    public final ColliderComponent<T> placedAt(final Transform transform) {
        applyTransform(transform);
        return this;
    }

    /// The farthest point of the placed shape along `direction`, in world space. `direction` need not
    /// be normalized and is never zero—[GjkIntersection] guards that before asking.
    ///
    /// This is the whole of what a shape tells the collision system: [GjkIntersection] decides any
    /// pair of shapes from their two support functions alone.
    public abstract Vec3 support(final Vec3 direction);

    /// Creates a [AABB] that wraps the space where the collider can exist
    ///
    /// Before checking collision, colliders are filtered by outer [AABB]
    /// to optimize detection algorithm
    public abstract AABB makeOuterAABB();

    public final AABB getOuterAABB() {
        return outerAABB;
    }
}
