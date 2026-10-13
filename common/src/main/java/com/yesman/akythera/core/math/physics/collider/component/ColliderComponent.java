package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.GjkIntersection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A [Collider] at a transform — the only form collision is ever checked against
/// ([CollisionDetectionLogics#overlap]).
///
/// Its [#localTransform] starts at the collider's pivot. Where the collider sits is [#applyTransform]'s argument,
/// the space the pivot is placed in by [#transformInheritPolicy], decided by whatever runs the query — [#parentModelTransform] for one attached under a
/// patch, a projectile's own position, a raw [AABB]. One built for a query alone has no host.
///
/// Subclasses call [#applyTransform] at the end of their own constructor: their vertex and normal
/// arrays are instance field initializers, still null while `super()` runs.
public abstract class ColliderComponent<T extends Collider> implements TransformComponent {
    /// Holding collider
    private final T collider;

    /// The host this collider is attached under, null for one built for a query alone.
    private final @Nullable ComponentAttachable host;

    /// The pivot, in the space [#applyTransform] places it in.
    private Transform localTransform;

    /// An [AABB] that wraps the space where the collider can exist
    protected AABB outerAABB;

    /// Whether this collider takes part, see [PatchComponent#isEnabled]. Starts at its [Collider]'s.
    private boolean enabled;

    protected ColliderComponent(final T collider, final @Nullable ComponentAttachable host) {
        this.collider = collider;
        this.host = host;
        this.localTransform = collider.getPivotTransform();
        this.enabled = collider.enabled();
    }

    public final T getCollider() {
        return collider;
    }

    public final ColliderType type() {
        return collider.type();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /// Places the shape: `transform` is the space [#localTransform] is placed in by [#transformInheritPolicy].
    public abstract void applyTransform(final Transform transform);

    /// How much a radius grows in `transform`, the space [#applyTransform] places the shape in: the largest scale
    /// axis of an inherited scale, since a ball cannot stretch along one axis alone.
    protected final double radiusScale(final Transform transform) {
        if (!transformInheritPolicy().inheritsScale()) return 1.0D;

        Vec3 scale = transform.sca();

        return Math.max(Math.abs(scale.x()), Math.max(Math.abs(scale.y()), Math.abs(scale.z())));
    }

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

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    /// Takes effect on the next [#applyTransform].
    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public @Nullable ComponentAttachable host() {
        return host;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return collider.getTransformInheritPolicy();
    }
}
