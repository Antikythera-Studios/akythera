package com.yesman.akythera.core.math.physics.collider;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.SweptColliderComponent;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

/// Cuts one tick of a collider's motion into slices short enough to sweep as straight hulls.
///
/// A point at radius `r` turning by `theta` leaves its own chord by `r * (1 - cos(theta / 2))`. Slice
/// the turn into `n` and that becomes `r * (1 - cos(theta / 2n))`, so `n` is the smallest count
/// putting it under [#GAP_TOLERANCE]. Pure translation needs one slice however far it moved—a
/// straight hull covers a straight motion exactly.
public final class SweptColliderQuery {
    private SweptColliderQuery() {}

    /// How far the swept arc may bow outside the hulls that stand in for it, in blocks. Under the
    /// radius of any authored blade, so a gap this size cannot open a hole a target fits through.
    private static final double GAP_TOLERANCE = 0.02D;

    /// Slices one tick will ever be cut into. A turn past this per tick is a teleport, not a swing.
    private static final int MAX_SLICES = 16;

    /// The slices covering `collider`'s motion from `previous` to `current`, in order. Each is a hull
    /// of the shape at the two ends of its own slice; a hit against any of them is a hit.
    public static List<SweptColliderComponent> slice(
        final Collider collider, final Transform previous, final Transform current
    ) {
        int slices = sliceCount(previous, current, reachOf(collider));
        List<SweptColliderComponent> out = new ArrayList<>(slices);

        for (int i = 0; i < slices; i++) {
            out.add(SweptColliderComponent.between(
                collider,
                Transform.lerp(previous, current, (float) i / slices),
                Transform.lerp(previous, current, (float) (i + 1) / slices)
            ));
        }

        return out;
    }

    /// How far the shape reaches from its socket, which is the radius the turn swings it around.
    private static double reachOf(final Collider collider) {
        ColliderComponent<?> placed = collider.instantiate(null).placedAt(Transform.identity());
        AABB box = placed.getOuterAABB();

        return Math.max(
            Math.max(Math.abs(box.minX), Math.abs(box.maxX)),
            Math.max(
                Math.max(Math.abs(box.minY), Math.abs(box.maxY)),
                Math.max(Math.abs(box.minZ), Math.abs(box.maxZ))
            )
        );
    }

    private static int sliceCount(final Transform previous, final Transform current, final double reach) {
        Quaterniond delta = previous.rot().difference(current.rot(), new Quaterniond()).normalize();
        // Not `Quaterniond#angle`: that reads 2 * acos(w), which runs past PI for the antipodal
        // quaternion of the very same rotation, so a small turn can read as nearly a full one and
        // demand every slice. The absolute value of w folds the two representations together.
        double turn = 2.0D * Math.acos(Math.min(1.0D, Math.abs(delta.w())));

        if (turn <= 0.0D || reach <= 0.0D) return 1;

        // Beyond this the arc cannot bow past the tolerance however fast it turns, so one slice does.
        double widest = 2.0D * Math.acos(Math.max(-1.0D, 1.0D - GAP_TOLERANCE / reach));

        return Math.clamp((int) Math.ceil(turn / widest), 1, MAX_SLICES);
    }
}
