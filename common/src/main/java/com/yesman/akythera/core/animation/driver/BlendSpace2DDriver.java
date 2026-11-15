package com.yesman.akythera.core.animation.driver;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation.Triangle;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
/// to the *winner* — the blended animation with the highest weight.
/// Mirrors UE5's blend-space behaviour where notifies
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

    /// Last phase a frame ran the winner node's notifies to. Mirrors `AnimationPlayer#lastFrameTime`.
    private float lastFramePhase;

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

    /// The animations the pose blends, one per animation the nodes name. Their weights ease toward the cached
    /// triangle's at [BlendSpace2D#weightSpeed].
    private final List<Sample> samples = new ArrayList<>();

    /// Sum of the [#samples] weights.
    private float weightSum;

    /// The triangle the [#samples]' animations were last resolved for, or null before the first resolve.
    private @Nullable Triangle resolvedTriangle;

    /// Whether this driver logged a node naming a missing animation. It logs only the first.
    private boolean warnedMissingAnimation;

    /// Weighted loop length of the [#samples] mix, in seconds. Recomputed every [#update].
    private float cachedCycleTime;

    /// The sample with the highest weight, or null before the first [#start].
    private @Nullable Sample winner;

    public BlendSpace2DDriver(
        final Holder<BlendSpace2D> holder,
        final Supplier<Float> xUpdater,
        final Supplier<Float> yUpdater
    ) {
        this.blendSpace2D = holder;
        this.xUpdater = xUpdater;
        this.yUpdater = yUpdater;
    }

    @Override
    public Pose getPose() {
        List<Pair<Pose, Float>> poses = new ArrayList<>(samples.size());

        for (Sample sample : samples) poses.add(Pair.of(sample.animation.getPose(timeOf(sample.animation, phase)), sample.weight));

        return PoseOperators.blendPoses(poses);
    }

    @Override
    public PlayableAnimation getPlayingAnimation() {
        return blendSpace2D.value();
    }

    /// Blended root motion at `time`: a mix of the [#samples]' root motions, using the same weights as
    /// [#getPose]. This is the value [RootMotionComponent] would consume for a blend space (translation only,
    /// matching how root motion is applied).
    ///
    /// Experimental and TODO: test blend space root motion
    public Transform getBlendedRootMotion(final float time) {
        float timePhase = MathUtils.safeDivision(time, cachedCycleTime, 0.0F);
        Vec3 loc = Vec3.ZERO;

        for (Sample sample : samples) {
            float weight = MathUtils.safeDivision(sample.weight, weightSum, 0.0F);
            loc = loc.add(sample.animation.getRootMotionTransform(timeOf(sample.animation, timePhase)).loc().scale(weight));
        }

        return Transform.fromTranslation(loc);
    }

    /// Runs the winner's notifies of the time the last frame moved to, whose pose is the one drawn now, then moves the
    /// time.
    @Override
    public void update(final float frameTime) {
        // The owner is known from the first tick on. Until then, the frames' notifies wait
        if (owner != null) {
            if (!terminated && cachedTriangleIndex != NO_TRIANGLE) {
                PlayableAnimation winner = winner();
                winner.frameUpdate(owner, timeOf(winner, lastFramePhase), timeOf(winner, phase));
            }

            lastFramePhase = phase;
        }

        if (fadingInOrOut) {
            fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, blendSpace2D.value().fadeInTime());
        } else {
            fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, blendSpace2D.value().fadeOutTime());
        }

        resolveWeights(frameTime);

        phase += MathUtils.safeDivision(frameTime, cachedCycleTime, 0.0F);

        if (phase >= 1.0F) {
            if (blendSpace2D.value().loops()) {
                phase = Mth.frac(phase);
            } else {
                phase = 1.0F;
                isFinished = true;
            }
        }
    }

    /// Moves the inputs, the cached triangle and the [#samples]' weights to this frame, snapping to the inputs right after
    /// [#start]. Picks the [#winner] and the weighted loop length.
    private void resolveWeights(final float frameTime) {
        BlendSpace2D blendSpace = blendSpace2D.value();
        DelaunayTriangulation<BlendSpace2D.AnimationNode> nodeSpace = blendSpace.getNodeSpace();
        boolean snap = snapToInputs;
        float stepX = snap || blendSpace.smoothX() <= 0.0F ? Float.MAX_VALUE : blendSpace.smoothX() * frameTime;
        float stepY = snap || blendSpace.smoothY() <= 0.0F ? Float.MAX_VALUE : blendSpace.smoothY() * frameTime;
        float weightStep = snap || blendSpace.weightSpeed() <= 0.0F ? Float.MAX_VALUE : blendSpace.weightSpeed() * frameTime;

        snapToInputs = false;

        targetX = blendSpace.wrapX()
            ? MathUtils.approachWrapped(targetX, xUpdater.get(), stepX, nodeSpace.minX(), nodeSpace.maxX())
            : Math.clamp(targetX + MathUtils.absoluteClamp(xUpdater.get() - targetX, stepX), nodeSpace.minX(), nodeSpace.maxX());
        targetY = blendSpace.wrapY()
            ? MathUtils.approachWrapped(targetY, yUpdater.get(), stepY, nodeSpace.minY(), nodeSpace.maxY())
            : Math.clamp(targetY + MathUtils.absoluteClamp(yUpdater.get() - targetY, stepY), nodeSpace.minY(), nodeSpace.maxY());

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

        float cycleTime = 0.0F;
        weightSum = 0.0F;
        winner = null;

        for (Sample sample : samples) {
            sample.weight += MathUtils.absoluteClamp(sample.target - sample.weight, weightStep);

            if (sample.weight > 0.0F) {
                if (winner == null || sample.weight > winner.weight) winner = sample;

                weightSum += sample.weight;
                cycleTime += sample.animation.getTotalFrameTime() / sample.animation.playRate() * sample.weight;
            }
        }

        samples.removeIf(sample -> sample.weight <= 0.0F && sample.target <= 0.0F);
        cachedCycleTime = MathUtils.safeDivision(cycleTime, weightSum, 0.0F);
    }

    private float timeOf(final PlayableAnimation animation, final float atPhase) {
        float shifted = atPhase + animation.phaseOffset();

        return (blendSpace2D.value().loops() ? Mth.frac(shifted) : Math.min(shifted, 1.0F)) * animation.getTotalFrameTime();
    }

    /// Recomputes barycentric weights for `triangleIndex` and makes them the
    /// [#samples]' targets. Returns `true` if (x, y) is inside the triangle
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

        boolean resolve = t != resolvedTriangle;

        for (Sample sample : samples) sample.target = 0.0F;

        addTarget(p1, w[0], resolve);
        addTarget(p2, w[1], resolve);
        addTarget(p3, w[2], resolve);
        resolvedTriangle = t;

        return true;
    }

    /// Adds `weight` to the target of the sample `node` plays, re-resolving its animation when `resolve`.
    private void addTarget(final BlendSpace2D.AnimationNode node, final float weight, final boolean resolve) {
        for (Sample sample : samples) {
            if (sample.mirrored == node.mirrored() && Objects.equals(sample.key, node.animation())) {
                if (resolve) sample.animation = resolveAnimation(node);

                sample.target += weight;
                return;
            }
        }

        Sample sample = new Sample(node.animation(), node.mirrored(), resolveAnimation(node));
        sample.target = weight;
        samples.add(sample);
    }

    /// The animation `node` plays, or the empty animation when its key names no entry.
    private PlayableAnimation resolveAnimation(final BlendSpace2D.AnimationNode node) {
        return node.resolveAnimation().orElseGet(() -> {
            if (!warnedMissingAnimation) {
                Akythera.LOGGER.warn("Blend space node at ({}, {}) names a missing animation {}", node.x(), node.y(), node.animation());
                warnedMissingAnimation = true;
            }

            return PlayableAnimation.EMPTY_ANIM_HOLDER.get().value();
        });
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

    /// Ends the winner's notify states the playback sits inside, for a play that stops before its end.
    private void interruptNotifies() {
        if (owner == null || terminated || cachedTriangleIndex == NO_TRIANGLE) return;

        PlayableAnimation winner = winner();
        winner.interrupt(owner, timeOf(winner, lastUpdatedPhase), timeOf(winner, lastFramePhase));
    }

    private PlayableAnimation winner() {
        return winner == null ? blendSpace2D.value() : winner.animation;
    }

    /// Runs on the blended clock: the phase times the weighted loop length of the [#samples] mix.
    @Override
    public float getTime() {
        return phase * cachedCycleTime;
    }

    @Override
    public float getPhase() {
        return phase;
    }

    /// Reads the winner's notify track, as [#tick] drives it.
    @Override
    public <T extends AnimationNotifyState<?>> @Nullable T getActiveNotifyState(final Class<T> type) {
        return terminated || winner == null ? null : winner.animation.getNotifyStateAt(type, timeOf(winner.animation, phase));
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
        lastFramePhase = 0.0F;
        cachedTriangleIndex = NO_TRIANGLE;
        resolvedTriangle = null;
        snapToInputs = true;
        resolveWeights(0.0F);
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
        lastFramePhase = phase;
        isFinished = source.isFinished();
    }

    /// One animation the pose blends, shared by the nodes that name it.
    private static final class Sample {
        private final @Nullable ResourceKey<? extends @NonNull PlayableAnimation> key;
        private final boolean mirrored;
        private PlayableAnimation animation;

        /// The weight the control point gives this animation, summed over the cached triangle's corners.
        private float target;

        /// The weight the pose blends this animation at, easing toward [#target].
        private float weight;

        private Sample(final @Nullable ResourceKey<? extends @NonNull PlayableAnimation> key, final boolean mirrored, final PlayableAnimation animation) {
            this.key = key;
            this.mirrored = mirrored;
            this.animation = animation;
        }
    }
}