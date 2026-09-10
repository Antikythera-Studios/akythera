package com.yesman.akythera.core.math.physics.collider;

import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.util.helper.MathUtils;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Whether two convex shapes overlap, decided from their support functions alone
/// ([ColliderComponent#support]).
///
/// The Minkowski difference of two convex sets contains the origin exactly when the sets intersect,
/// and the support function of that difference is `a.support(d) - b.support(-d)`. So the search never
/// builds the difference: it grows a simplex of at most four points of it toward the origin, and
/// stops as soon as the origin is enclosed or a supporting plane separates it.
///
/// This is what any [ColliderType] pair falls back to when [CollisionDetectionLogics] holds no
/// registered logic for it, which is every pair a cone or a box takes part in.
public final class GjkIntersection {
    private GjkIntersection() {}

    /// Enough for four convex primitives; a pair that has not converged by here is grazing, and
    /// reporting no overlap for a graze is the safe answer.
    private static final int MAX_ITERATIONS = 32;

    /// Below this the search direction has collapsed, which means the origin sits on the boundary of
    /// the Minkowski difference—a touch, reported as an overlap.
    private static final double DEGENERATE = 1.0E-12D;

    public static boolean overlap(final ColliderComponent<?> collider1, final ColliderComponent<?> collider2) {
        Vec3 seed = collider2.getOuterAABB().getCenter().subtract(collider1.getOuterAABB().getCenter());
        List<Vec3> simplex = new ArrayList<>(4);

        simplex.add(minkowskiSupport(collider1, collider2, seed.lengthSqr() > DEGENERATE ? seed : MathUtils.XP));

        Vec3 direction = simplex.getFirst().scale(-1.0D);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (direction.lengthSqr() <= DEGENERATE) return true;

            Vec3 point = minkowskiSupport(collider1, collider2, direction);

            // The farthest point of the difference along the search direction fell short of the
            // origin, so that direction is a separating axis.
            if (point.dot(direction) < 0.0D) return false;

            simplex.add(point);
            direction = nextDirection(simplex);

            if (direction == null) return true;
        }

        return false;
    }

    private static Vec3 minkowskiSupport(
        final ColliderComponent<?> collider1, final ColliderComponent<?> collider2, final Vec3 direction
    ) {
        return collider1.support(direction).subtract(collider2.support(direction.scale(-1.0D)));
    }

    /// Trims `simplex` to the feature nearest the origin and returns the direction to search next,
    /// or null once the simplex is a tetrahedron enclosing the origin.
    ///
    /// The newest point is always last, and the caller has just proven the origin lies on its side of
    /// the previous feature, so only the regions touching that point are tested.
    private static @Nullable Vec3 nextDirection(final List<Vec3> simplex) {
        Vec3 a = simplex.getLast();
        Vec3 toOrigin = a.scale(-1.0D);

        if (simplex.size() == 2) {
            Vec3 ab = simplex.get(0).subtract(a);

            if (ab.dot(toOrigin) < 0.0D) {
                keep(simplex, a);
                return toOrigin;
            }

            return ab.cross(toOrigin).cross(ab);
        }

        if (simplex.size() == 3) {
            Vec3 b = simplex.get(1);
            Vec3 c = simplex.get(0);
            Vec3 ab = b.subtract(a);
            Vec3 ac = c.subtract(a);
            Vec3 abc = ab.cross(ac);

            if (abc.cross(ac).dot(toOrigin) > 0.0D) {
                keep(simplex, c, a);
                return ac.cross(toOrigin).cross(ac);
            }

            if (ab.cross(abc).dot(toOrigin) > 0.0D) {
                keep(simplex, b, a);
                return ab.cross(toOrigin).cross(ab);
            }

            if (abc.dot(toOrigin) > 0.0D) return abc;

            // Origin is under the triangle. Swap two points so the face normals the tetrahedron case
            // builds keep pointing away from the simplex.
            keep(simplex, b, c, a);
            return abc.scale(-1.0D);
        }

        Vec3 b = simplex.get(2);
        Vec3 c = simplex.get(1);
        Vec3 d = simplex.get(0);
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        Vec3 ad = d.subtract(a);
        Vec3 abc = ab.cross(ac);
        Vec3 acd = ac.cross(ad);
        Vec3 adb = ad.cross(ab);

        if (abc.dot(toOrigin) > 0.0D) {
            keep(simplex, c, b, a);
            return abc;
        }

        if (acd.dot(toOrigin) > 0.0D) {
            keep(simplex, d, c, a);
            return acd;
        }

        if (adb.dot(toOrigin) > 0.0D) {
            keep(simplex, b, d, a);
            return adb;
        }

        return null;
    }

    private static void keep(final List<Vec3> simplex, final Vec3... points) {
        simplex.clear();
        simplex.addAll(List.of(points));
    }
}
