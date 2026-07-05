package com.yesman.akythera.core.animation.driver;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation.Triangle;
import com.yesman.akythera.core.util.helper.MathUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/// Instantiated version of [BlendSpace2D]. To keep the design intention of
/// [PlayableAnimation] and [AnimationDriver].
///
/// Triangle cache: the driver remembers the index of the last triangle that
/// contained the control point. Each [#update] first re-tests that triangle
/// — barycentric weights only fail when the control point genuinely crossed
/// an edge, which is rare relative to update rate. The full triangulation
/// walk (`findContainingTriangle`) is reserved for actual transitions.
///
/// Notify dispatch: [#tick] forwards the (prevElapsed, currentElapsed) pair
/// to the *winner* node — the corner of the cached triangle with the highest
/// barycentric weight. Mirrors UE5's blend-space behaviour where notifies
/// fire from the dominant input animation, not blended across all corners
/// (which would multi-fire identical notifies near edges).
public final class BlendSpace2DDriver implements AnimationDriver {
    /// Represents that, "no triangle cached yet"
    private static final int NO_TRIANGLE = -1;

    private final Holder.Reference<? extends @NonNull BlendSpace2D> blendSpace2D;
    private final Supplier<Float> xUpdater;
    private final Supplier<Float> yUpdater;

    private float fadeTime;
    private boolean isFinished;
    /// true if fading in, false if fading out
    private boolean fadingInOrElseOut;
    private float elapsedTime;

    /// Last [AnimationDriver#tick] elapsedTime — passed as `prevElapsedTime`
    /// to the winner node's notify dispatch. Mirrors
    /// `AnimationPlayer#lastUpdatedTime`.
    private float lastUpdatedTime;

    private float targetX;
    private float targetY;

    /// Cached enclosing triangle. -1 until the first update finds one.
    private int cachedTriangleIndex = NO_TRIANGLE;

    /// Cached node refs + weights for the cached triangle. Populated by [#refreshCachedTriangle].
    private PlayableAnimation cachedAnim1;
    private PlayableAnimation cachedAnim2;
    private PlayableAnimation cachedAnim3;
    private float cachedWeight1;
    private float cachedWeight2;
    private float cachedWeight3;

    /// Weighted clip length of the cached corner mix — the wall-clock loop bound.
    /// Recomputed every [#update]; MAX_VALUE means "no usable mix yet" and callers
    /// fall back to raw elapsed-time sampling.
    private float cachedTotalTime = Float.MAX_VALUE;

    /// Index *within the cached triangle* (0,1,2) of the winner. Set when the
    /// cached weights are refreshed; consumed by [#tick] to address the
    /// matching `cachedAnimN` field.
    private int winnerDot;

    public BlendSpace2DDriver(
        final Holder.Reference<? extends @NonNull BlendSpace2D> holder,
        final Supplier<Float> xUpdater,
        final Supplier<Float> yUpdater
    ) {
        blendSpace2D = holder;
        this.xUpdater = xUpdater;
        this.yUpdater = yUpdater;

        // Equal-weight fallback so getPose() before the first update doesn't NPE.
        // Re-uses the blend-space value itself as a benign placeholder; overwritten
        // on the first successful update.
        cachedAnim1 = blendSpace2D.value();
        cachedAnim2 = cachedAnim1;
        cachedAnim3 = cachedAnim1;
        cachedWeight1 = 1.0F / 3.0F;
        cachedWeight2 = 1.0F / 3.0F;
        cachedWeight3 = 1.0F / 3.0F;
        winnerDot = 0;
    }

    @Override
    public Pose getPose() {
        // Phase-sync: the clock wraps at the *weighted* total (see update), so sampling
        // corners at raw elapsedTime freezes any shorter clip on its last keyframe until
        // the wrap. Sample each corner at the shared phase scaled to its own length.
        float t1, t2, t3;
        if (cachedTotalTime > 0.0F && cachedTotalTime != Float.MAX_VALUE) {
            float phase = elapsedTime / cachedTotalTime;
            t1 = phase * cachedAnim1.getTotalFrameTime();
            t2 = phase * cachedAnim2.getTotalFrameTime();
            t3 = phase * cachedAnim3.getTotalFrameTime();
        } else {
            t1 = t2 = t3 = elapsedTime;
        }

        return Pose.blendPoses(
            Pair.of(cachedAnim1.getPose(t1), cachedWeight1),
            Pair.of(cachedAnim2.getPose(t2), cachedWeight2),
            Pair.of(cachedAnim3.getPose(t3), cachedWeight3)
        );
    }

    @Override
    public PlayableAnimation getPlayingAnimation() {
        return blendSpace2D.value();
    }

    /// Blended root motion at `time`: a barycentric mix of the cached triangle corners' root motions,
    /// using the same weights as [#getPose]. This is the value [com.yesman.akythera.api.animation.RootMotionComponent]
    /// would consume for a blend space (translation only, matching how root motion is applied).
    ///
    /// Experimental and TODO: test blend space root motion
    public Transform getBlendedRootMotion(final float time) {
        float t1, t2, t3;
        if (cachedTotalTime > 0.0F && cachedTotalTime != Float.MAX_VALUE) {
            float phase = time / cachedTotalTime;
            t1 = phase * cachedAnim1.getTotalFrameTime();
            t2 = phase * cachedAnim2.getTotalFrameTime();
            t3 = phase * cachedAnim3.getTotalFrameTime();
        } else {
            t1 = t2 = t3 = time;
        }

        Vec3 loc = cachedAnim1.getRootMotionTransform(t1).loc().scale(cachedWeight1)
            .add(cachedAnim2.getRootMotionTransform(t2).loc().scale(cachedWeight2))
            .add(cachedAnim3.getRootMotionTransform(t3).loc().scale(cachedWeight3));

        return Transform.fromTranslation(loc);
    }

    @Override
    public void update(final float frameTime) {
        if (fadingInOrElseOut) {
            fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, blendSpace2D.value().fadeInTime());
        } else {
            fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, blendSpace2D.value().fadeOutTime());
        }

        elapsedTime += frameTime;

        DelaunayTriangulation<BlendSpace2D.AnimationNode> nodeSpace = blendSpace2D.value().getNodeSpace();
        float smoothX = blendSpace2D.value().smoothX() <= 0.0F ? Float.MAX_VALUE : blendSpace2D.value().smoothX();
        float smoothY = blendSpace2D.value().smoothY() <= 0.0F ? Float.MAX_VALUE : blendSpace2D.value().smoothY();

        targetX = Math.clamp(
            targetX + MathUtils.absoluteClamp(xUpdater.get() - targetX, smoothX * frameTime),
            nodeSpace.minX(), nodeSpace.maxX()
        );
        targetY = Math.clamp(
            targetY + MathUtils.absoluteClamp(yUpdater.get() - targetY, smoothY * frameTime),
            nodeSpace.minY(), nodeSpace.maxY()
        );

        // Fast path: the cached triangle still contains the control point.
        // Common case — control inputs drift smoothly between ticks.
        boolean cacheHit = false;
        if (cachedTriangleIndex != NO_TRIANGLE) {
            cacheHit = refreshCachedTriangle(nodeSpace, cachedTriangleIndex, targetX, targetY);
        }

        // Slow path: walk the full triangle list to relocate the control point.
        if (!cacheHit) {
            int found = nodeSpace.findContainingTriangle(targetX, targetY);
            if (found != -1) {
                cachedTriangleIndex = found;
                refreshCachedTriangle(nodeSpace, found, targetX, targetY);
            }
            // If found == -1 the control point is outside every triangle (e.g.
            // a degenerate node space with < 3 nodes). Keep the previous cache
            // untouched so getPose() returns whatever was last sane.
        }

        // Wall-clock bound for the current corner mix
        cachedTotalTime =
            cachedAnim1.getTotalFrameTime() * cachedWeight1 +
            cachedAnim2.getTotalFrameTime() * cachedWeight2 +
            cachedAnim3.getTotalFrameTime() * cachedWeight3;

        if (elapsedTime > cachedTotalTime) {
            if (blendSpace2D.value().loops()) {
                elapsedTime %= cachedTotalTime;
            } else {
                elapsedTime = cachedTotalTime;
                isFinished = true;
            }
        }
    }

    /// Recomputes barycentric weights for `triangleIndex` and writes them
    /// into the `cachedAnimN` / `cachedWeightN` slots, plus picks the
    /// `winnerCorner`. Returns `true` if (x, y) is inside the triangle
    /// (within EPSILON tolerance), `false` otherwise.
    private boolean refreshCachedTriangle(
        final DelaunayTriangulation<BlendSpace2D.AnimationNode> nodeSpace,
        final int triangleIndex,
        final float x,
        final float y
    ) {
        var triangles = nodeSpace.getTriangles();
        if (triangleIndex < 0 || triangleIndex >= triangles.size()) return false;
        Triangle t = triangles.get(triangleIndex);

        var p1 = nodeSpace.getPoint(t.a());
        var p2 = nodeSpace.getPoint(t.b());
        var p3 = nodeSpace.getPoint(t.c());
        float[] w = nodeSpace.barycentricWeightsOf(x, y, p1, p2, p3);

        // Outside the cached triangle → caller falls back to a full walk.
        if (w[0] < -MathUtils.EPSILON || w[1] < -MathUtils.EPSILON || w[2] < -MathUtils.EPSILON) {
            return false;
        }

        cachedAnim1 = p1.animation().value();
        cachedAnim2 = p2.animation().value();
        cachedAnim3 = p3.animation().value();
        cachedWeight1 = w[0];
        cachedWeight2 = w[1];
        cachedWeight3 = w[2];

        // Winner = the corner with the largest weight.
        if (w[0] >= w[1] && w[0] >= w[2]) winnerDot = 0;
        else if (w[1] >= w[2]) winnerDot = 1;
        else winnerDot = 2;

        return true;
    }

    @Override
    public void tick(final EntityPatch<Entity> owner) {
        // Drive only the winner's notify track.
        PlayableAnimation winner = switch (winnerDot) {
            case 0 -> cachedAnim1;
            case 1 -> cachedAnim2;
            default -> cachedAnim3;
        };

        // Map blended-clock times onto the winner clip's own timeline (phase sync,
        // mirrors getPose) so notify keyframes fire at the authored moments.
        float scale = (cachedTotalTime > 0.0F && cachedTotalTime != Float.MAX_VALUE)
            ? winner.getTotalFrameTime() / cachedTotalTime : 1.0F;

        winner.tick(owner, lastUpdatedTime * scale, elapsedTime * scale);
        lastUpdatedTime = elapsedTime;
    }

    /// Runs on the blended clock: wraps at the weighted total of the cached corner
    /// mix. getPose() maps it onto each corner's own timeline (phase sync).
    @Override
    public float getTime() {
        return elapsedTime;
    }

    @Override
    public void start() {
        fadingInOrElseOut = true;
        isFinished = false;
        elapsedTime = 0.0F;
        lastUpdatedTime = 0.0F;
        cachedTriangleIndex = NO_TRIANGLE;
    }

    @Override
    public void terminate() {
        fadingInOrElseOut = false;
    }

    @Override
    public float getWeight() {
        if (fadingInOrElseOut) {
            return fadeTime / blendSpace2D.value().fadeInTime();
        } else {
            return fadeTime / blendSpace2D.value().fadeOutTime();
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }
}