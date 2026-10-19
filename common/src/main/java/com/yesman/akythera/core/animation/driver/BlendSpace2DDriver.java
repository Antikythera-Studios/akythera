package com.yesman.akythera.core.animation.driver;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation.Triangle;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    private final Holder<BlendSpace2D> blendSpace2D;
    private final Supplier<Float> xUpdater;
    private final Supplier<Float> yUpdater;

    private float fadeTime;
    private boolean isFinished;
    /// true if fading in, false if fading out
    private boolean fadingInOrOut;
    private float phase;

    /// Last [AnimationDriver#tick] phase, the start of the winner node's notify window. Mirrors
    /// `AnimationPlayer#lastUpdatedTime`.
    private float lastUpdatedPhase;

    /// Whether [#terminate] ran since the last [#start]. A terminated play fades out without driving notifies.
    private boolean terminated;

    /// The owner of the last [#tick], which a stopped play's notify states are ended for.
    private @Nullable ComponentAttachable owner;

    private float targetX;
    private float targetY;

    /// Whether the next [#update] takes the inputs as they are, without smoothing from the last ones.
    private boolean snapToInputs = true;

    /// Cached enclosing triangle. -1 until the first update finds one.
    private int cachedTriangleIndex = NO_TRIANGLE;

    /// Cached node refs + weights for the cached triangle. Populated by [#refreshCachedTriangle].
    private PlayableAnimation cachedAnim1;
    private PlayableAnimation cachedAnim2;
    private PlayableAnimation cachedAnim3;
    private float cachedWeight1;
    private float cachedWeight2;
    private float cachedWeight3;

    /// Weighted loop length of the cached corner mix, in seconds. Recomputed every [#update].
    private float cachedCycleTime;

    /// Index *within the cached triangle* (0,1,2) of the winner. Set when the
    /// cached weights are refreshed; consumed by [#tick] to address the
    /// matching `cachedAnimN` field.
    private int winnerDot;

    public BlendSpace2DDriver(
        final Holder<BlendSpace2D> holder,
        final Supplier<Float> xUpdater,
        final Supplier<Float> yUpdater
    ) {
        this.blendSpace2D = holder;
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
        return PoseOperators.blendPoses(
            Pair.of(cachedAnim1.getPose(timeOf(cachedAnim1, phase)), cachedWeight1),
            Pair.of(cachedAnim2.getPose(timeOf(cachedAnim2, phase)), cachedWeight2),
            Pair.of(cachedAnim3.getPose(timeOf(cachedAnim3, phase)), cachedWeight3)
        );
    }

    @Override
    public PlayableAnimation getPlayingAnimation() {
        return blendSpace2D.value();
    }

    /// Blended root motion at `time`: a barycentric mix of the cached triangle corners' root motions,
    /// using the same weights as [#getPose]. This is the value [RootMotionComponent]
    /// would consume for a blend space (translation only, matching how root motion is applied).
    ///
    /// Experimental and TODO: test blend space root motion
    public Transform getBlendedRootMotion(final float time) {
        float timePhase = MathUtils.safeDivision(time, cachedCycleTime, 0.0F);

        Vec3 loc = cachedAnim1.getRootMotionTransform(timeOf(cachedAnim1, timePhase)).loc().scale(cachedWeight1)
            .add(cachedAnim2.getRootMotionTransform(timeOf(cachedAnim2, timePhase)).loc().scale(cachedWeight2))
            .add(cachedAnim3.getRootMotionTransform(timeOf(cachedAnim3, timePhase)).loc().scale(cachedWeight3));

        return Transform.fromTranslation(loc);
    }

    @Override
    public void update(final float frameTime) {
        if (fadingInOrOut) {
            fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, blendSpace2D.value().fadeInTime());
        } else {
            fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, blendSpace2D.value().fadeOutTime());
        }

        BlendSpace2D blendSpace = blendSpace2D.value();
        DelaunayTriangulation<BlendSpace2D.AnimationNode> nodeSpace = blendSpace.getNodeSpace();
        float smoothX = blendSpace.smoothX() <= 0.0F ? Float.MAX_VALUE : blendSpace.smoothX();
        float smoothY = blendSpace.smoothY() <= 0.0F ? Float.MAX_VALUE : blendSpace.smoothY();

        if (snapToInputs) {
            smoothX = Float.MAX_VALUE;
            smoothY = Float.MAX_VALUE;
            snapToInputs = false;
        }

        targetX = blendSpace.wrapX()
            ? MathUtils.approachWrapped(targetX, xUpdater.get(), smoothX * frameTime, nodeSpace.minX(), nodeSpace.maxX())
            : Math.clamp(targetX + MathUtils.absoluteClamp(xUpdater.get() - targetX, smoothX * frameTime), nodeSpace.minX(), nodeSpace.maxX());
        targetY = blendSpace.wrapY()
            ? MathUtils.approachWrapped(targetY, yUpdater.get(), smoothY * frameTime, nodeSpace.minY(), nodeSpace.maxY())
            : Math.clamp(targetY + MathUtils.absoluteClamp(yUpdater.get() - targetY, smoothY * frameTime), nodeSpace.minY(), nodeSpace.maxY());

        // Fast path: the cached triangle still contains the control point.
        // Common case — control inputs drift smoothly between ticks.
        boolean cacheHit = false;
        if (cachedTriangleIndex != NO_TRIANGLE) {
            cacheHit = refreshCachedTriangle(nodeSpace, cachedTriangleIndex, targetX, targetY);
        }

        // Slow path: walk the full triangle list to relocate the control point.
        if (!cacheHit) {
            int found = nodeSpace.findContainingTriangle(targetX, targetY);

            // If found == -1 the control point is outside every triangle (e.g.
            // a degenerate node space with < 3 nodes). Keep the previous cache
            // untouched so getPose() returns whatever was last sane.
            if (found != -1) {
                cachedTriangleIndex = found;
                refreshCachedTriangle(nodeSpace, found, targetX, targetY);
            }
        }

        cachedCycleTime =
            cachedAnim1.getTotalFrameTime() / cachedAnim1.rateScale() * cachedWeight1 +
            cachedAnim2.getTotalFrameTime() / cachedAnim2.rateScale() * cachedWeight2 +
            cachedAnim3.getTotalFrameTime() / cachedAnim3.rateScale() * cachedWeight3;

        phase += MathUtils.safeDivision(frameTime, cachedCycleTime, 0.0F);

        if (phase >= 1.0F) {
            if (blendSpace.loops()) {
                phase = Mth.frac(phase);
            } else {
                phase = 1.0F;
                isFinished = true;
            }
        }
    }

    private float timeOf(final PlayableAnimation animation, final float atPhase) {
        float shifted = atPhase + animation.phaseOffset();

        return (blendSpace2D.value().loops() ? Mth.frac(shifted) : Math.min(shifted, 1.0F)) * animation.getTotalFrameTime();
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

    /// Drives only the winner's notify track, and none once terminated.
    @Override
    public void tick(final ComponentAttachable owner) {
        this.owner = owner;

        if (!terminated) {
            PlayableAnimation winner = winner();
            winner.tick(owner, timeOf(winner, lastUpdatedPhase), timeOf(winner, phase));
        }

        lastUpdatedPhase = phase;
    }

    /// Ends the winner's notify states the last tick sits inside, for a play that stops before its end.
    private void interruptNotifies() {
        if (owner == null || terminated || cachedTriangleIndex == NO_TRIANGLE) return;

        PlayableAnimation winner = winner();
        winner.interrupt(owner, timeOf(winner, lastUpdatedPhase));
    }

    private PlayableAnimation winner() {
        return switch (winnerDot) {
            case 0 -> cachedAnim1;
            case 1 -> cachedAnim2;
            default -> cachedAnim3;
        };
    }

    /// Runs on the blended clock: the phase times the weighted loop length of the cached corner mix.
    @Override
    public float getTime() {
        return phase * cachedCycleTime;
    }

    @Override
    public float getPhase() {
        return phase;
    }

    /// Restarting a play that still runs interrupts it.
    @Override
    public void start() {
        interruptNotifies();
        fadingInOrOut = true;
        isFinished = false;
        terminated = false;
        phase = 0.0F;
        lastUpdatedPhase = 0.0F;
        cachedTriangleIndex = NO_TRIANGLE;
        snapToInputs = true;
    }

    @Override
    public void terminate() {
        interruptNotifies();
        fadingInOrOut = false;
        terminated = true;
    }

    @Override
    public float getWeight() {
        if (fadingInOrOut) {
            return MathUtils.safeDivision(fadeTime, blendSpace2D.value().fadeInTime(), 1.0F);
        } else {
            return MathUtils.safeDivision(fadeTime, blendSpace2D.value().fadeOutTime(), 0.0F);
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean fadingInOrOut() {
        return fadingInOrOut;
    }

    @Override
    public float getFadeTime() {
        return fadeTime;
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        phase = source.getPhase();
        lastUpdatedPhase = phase;
        isFinished = source.isFinished();
    }
}