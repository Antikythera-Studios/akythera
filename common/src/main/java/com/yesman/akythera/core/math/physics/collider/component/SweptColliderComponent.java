package com.yesman.akythera.core.math.physics.collider.component;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.CollisionDetectionLogics;
import com.yesman.akythera.core.math.physics.collider.GjkIntersection;
import com.yesman.akythera.core.math.physics.collider.SweptColliderQuery;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// One shape at two placements, tested as the convex hull of both.
///
/// The hull of two placements is exactly the volume a shape sweeps while it translates, and slightly
/// short of it while it rotates—an arc bows outside its chord. [SweptColliderQuery] is what keeps
/// that error bounded, by cutting a tick into slices short enough that each hull stays tight. A hull
/// spanning a whole fast rotation would also be wrong in the other direction: the hull of a spin is a
/// filled disc, and everything standing inside the circle would report a hit.
///
/// Carries no [Collider#type] of its own, so [CollisionDetectionLogics] finds no override entry and
/// every query lands on [GjkIntersection].
public final class SweptColliderComponent extends ColliderComponent<Collider> {
    private final ColliderComponent<?> from;
    private final ColliderComponent<?> to;

    public SweptColliderComponent(final ColliderComponent<?> from, final ColliderComponent<?> to) {
        super(from.getCollider());

        this.from = from;
        this.to = to;
        this.outerAABB = makeOuterAABB();
    }

    /// Places `collider` at both ends of one slice. Each end is its own component, so the two
    /// placements do not overwrite each other.
    public static SweptColliderComponent between(
        final Collider collider, final Transform fromTransform, final Transform toTransform
    ) {
        return new SweptColliderComponent(
            collider.instantiate(null).placedAt(fromTransform),
            collider.instantiate(null).placedAt(toTransform)
        );
    }

    /// Moves both ends at once. The pair of placements this was built with is lost.
    @Override
    public void applyTransform(final Transform transform) {
        from.applyTransform(transform);
        to.applyTransform(transform);

        outerAABB = makeOuterAABB();
    }

    @Override
    public Vec3 support(final Vec3 direction) {
        Vec3 atFrom = from.support(direction);
        Vec3 atTo = to.support(direction);

        return direction.dot(atFrom) >= direction.dot(atTo) ? atFrom : atTo;
    }

    @Override
    public AABB makeOuterAABB() {
        return from.getOuterAABB().minmax(to.getOuterAABB());
    }
}
