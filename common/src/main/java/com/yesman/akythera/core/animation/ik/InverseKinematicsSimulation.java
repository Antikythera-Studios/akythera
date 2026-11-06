package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.Terrain;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/// Places the [IKChain]s of one armature each frame: steps them over the ground, follows the [IKCurves] of the playing
/// animation, fits the chains' shared parent to the feet, and solves each chain. The [IKChannels] of the pose decide
/// how much of each happens.
public final class InverseKinematicsSimulation {
    /// Longest time one frame advances the steps by, in seconds.
    private static final float MAX_FRAME_TIME = 0.25F;

    /// A weight at or under this counts as none.
    private static final float WEIGHT_EPSILON = 1.0E-3F;

    /// How far a planted tip may end up from where the animation puts it before it plants there at once instead of
    /// stepping, in blocks. Covers a teleport.
    private static final double SNAP_DISTANCE = 4.0D;

    /// How long the body's measured motion takes to reach about two thirds of [#bodyVelocity] and [#bodyYawRate], in
    /// seconds.
    private static final double MOTION_SMOOTHING = 0.1D;

    /// Most [IKChainKinematics#settle] passes a chain takes after its solver.
    private static final int SETTLE_PASSES = 8;

    /// How close a solved tip must come to its target before [IKChainKinematics#settle] leaves it, in blocks.
    private static final double SETTLE_TOLERANCE = 1.0E-3D;

    /// The chains [#states] were made for.
    private List<IKChain> chains = List.of();

    /// The state of each of [#chains], in its order.
    private ChainState[] states = new ChainState[0];

    /// World targets that hold chain tips in place of their placement, by the chain's tip bone.
    private final Map<String, Vec3> targetOverrides = new HashMap<>();

    /// When the last [#apply] ran, in seconds; NaN before the first.
    private double lastTime = Double.NaN;

    /// Where the armature stood in the world in the last [#apply], null before the first.
    private @Nullable Vec3 lastBodyPosition;

    /// The body yaw of the last [#apply], in degrees.
    private double lastBodyYaw;

    /// The body's horizontal velocity, in blocks per second, smoothed over [#MOTION_SMOOTHING].
    private Vec3 bodyVelocity = Vec3.ZERO;

    /// The body's turn rate, in degrees per second, smoothed over [#MOTION_SMOOTHING].
    private double bodyYawRate;

    /// `pose` with the chains of `physics` placed and solved.
    ///
    /// @param armatureToWorld where the armature stands in the world
    /// @param terrain         the ground the chains step on
    /// @param time            now, in seconds on a clock every call shares
    public Pose apply(
        final Pose pose, final AssetAccessor<? extends Armature> armatureAccessor, final ArmaturePhysicsData physics,
        final Transform armatureToWorld, final Terrain terrain, final double time
    ) {
        Armature armature = armatureAccessor.get();
        List<IKChain> chains = physics.ikChains();

        if (armature == null || chains.isEmpty()) return pose;

        if (!this.chains.equals(chains)) {
            this.chains = chains;
            states = new ChainState[chains.size()];

            for (int i = 0; i < states.length; i++) states[i] = new ChainState();
        }

        float frameTime = Double.isNaN(lastTime) ? 0.0F : (float) Math.clamp(time - lastTime, 0.0D, MAX_FRAME_TIME);
        lastTime = time;

        IKChannels channels = pose.getIKChannels();
        Matrix4f[] bound = pose.getForwardBoundTransforms(armatureAccessor);
        Vec3 forward = armatureToWorld.mulDirection(MathUtils.ZP);
        double bodyYaw = Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        double floorHeight = armatureToWorld.loc().y();
        Vec3 bodyPosition = armatureToWorld.loc();

        if (lastBodyPosition != null && frameTime > 0.0F) {
            Vec3 moved = bodyPosition.subtract(lastBodyPosition);

            if (moved.length() > SNAP_DISTANCE) {
                bodyVelocity = Vec3.ZERO;
                bodyYawRate = 0.0D;
            } else {
                double blend = 1.0D - Math.exp(-frameTime / MOTION_SMOOTHING);
                bodyVelocity = bodyVelocity.lerp(new Vec3(moved.x(), 0.0D, moved.z()).scale(1.0D / frameTime), blend);
                bodyYawRate = Mth.lerp(blend, bodyYawRate, Mth.wrapDegrees(bodyYaw - lastBodyYaw) / frameTime);
            }
        }

        lastBodyPosition = bodyPosition;
        lastBodyYaw = bodyYaw;

        BodyMotion motion = new BodyMotion(bodyPosition, bodyYaw, bodyVelocity, bodyYawRate);

        @Nullable Vector3d[] targets = new Vector3d[chains.size()];
        @Nullable Bone[] chainRoots = new Bone[chains.size()];
        float[] weights = new float[chains.size()];
        @Nullable Vector3d[] footholds = new Vector3d[chains.size()];
        double[] reaches = new double[chains.size()];

        for (int i = 0; i < chains.size(); i++) {
            IKChain chain = chains.get(i);
            ChainState state = states[i];
            Bone tip = armature.getBoneByName(chain.tipBone());
            Vec3 override = targetOverrides.get(chain.tipBone());
            IKChannel channel = channels == null ? IKChannel.NONE : channels.get(chain.tipBone());

            if (tip == null || override == null && channel.alpha() <= WEIGHT_EPSILON) {
                state.release();
                continue;
            }

            Bone chainRoot = tip;
            Vector3f joint = bound[tip.getId()].transformPosition(new Vector3f(tip.getToTail()));

            for (int k = 0; k < chain.chainLength(); k++) {
                Vector3f head = bound[chainRoot.getId()].getTranslation(new Vector3f());
                reaches[i] += joint.distance(head);
                joint = head;

                if (k < chain.chainLength() - 1 && chainRoot.getParent() != null) chainRoot = chainRoot.getParent();
            }

            chainRoots[i] = chainRoot;

            IKChain.Stepping stepping = chain.stepping();
            Vec3 animatedTip = armatureToWorld.mul(VectorUtils.toVec3(bound[tip.getId()].transformPosition(new Vector3f(tip.getToTail()))));
            double rootHeight = armatureToWorld.mul(VectorUtils.toVec3(bound[chainRoot.getId()].getTranslation(new Vector3f()))).y();
            OptionalDouble ground = terrain.groundHeight(
                animatedTip.x(), animatedTip.z(), Math.max(rootHeight, animatedTip.y()), animatedTip.y() - stepping.rayMaxDistance()
            );
            Vec3 home = ground.isPresent()
                ? new Vec3(animatedTip.x(), ground.getAsDouble() + animatedTip.y() - floorHeight, animatedTip.z())
                : animatedTip;
            Vec3 placed;

            if (override != null) {
                placed = override;
                weights[i] = 1.0F;
            } else {
                String mirrorTip = armature.getMirrorBone(chain.tipBone());
                boolean mirrorSwinging = false;

                for (int j = 0; j < chains.size(); j++) {
                    if (j != i && chains.get(j).tipBone().equals(mirrorTip) && states[j].swinging()) mirrorSwinging = true;
                }

                Vec3 byCurve = state.placeByCurve(home, channel.plant());
                Vec3 stepped = channel.procedural() > WEIGHT_EPSILON
                    ? state.step(home, motion, stepping, frameTime, mirrorSwinging)
                    : state.plant(byCurve, bodyYaw);
                placed = animatedTip.lerp(byCurve.lerp(stepped, channel.procedural()), channel.alpha());
                weights[i] = channel.alpha();
            }

            state.placed = placed;
            state.animated = home;
            targets[i] = toVector3d(armatureToWorld.inverseMul(placed));

            if (override != null) {
                footholds[i] = targets[i];
            } else if (ground.isPresent()) {
                footholds[i] = toVector3d(armatureToWorld.inverseMul(new Vec3(animatedTip.x(), ground.getAsDouble(), animatedTip.z())));
            }
        }

        Pose result = new Pose(pose);
        ArmaturePhysicsData.RootFitting fitting = physics.rootFitting();

        if (fitting.enabled()) {
            Bone shared = null;
            double drop = 0.0D;
            float weightSum = 0.0F;
            List<Vector3d> footing = new ArrayList<>();

            for (int i = 0; i < chains.size(); i++) {
                Bone chainRoot = chainRoots[i];
                Vector3d target = targets[i];

                if (chainRoot == null || target == null || footholds[i] == null || chainRoot.getParent() == null) continue;

                Set<Bone> lineage = new HashSet<>();

                for (Bone ancestor = chainRoot.getParent(); ancestor != null; ancestor = ancestor.getParent()) lineage.add(ancestor);

                if (shared == null) {
                    shared = chainRoot.getParent();
                } else {
                    while (shared != null && !lineage.contains(shared)) shared = shared.getParent();
                }

                Vector3d hip = toVector3d(VectorUtils.toVec3(bound[chainRoot.getId()].getTranslation(new Vector3f())));
                double horizontal = Math.hypot(target.x - hip.x, target.z - hip.z);

                // A swinging tip is in the air, and no drop brings a tip out of reach horizontally any closer
                if (horizontal < reaches[i] && !states[i].swinging()) drop = Math.max(drop, hip.y - target.y - Math.sqrt(reaches[i] * reaches[i] - horizontal * horizontal));

                weightSum += weights[i];
                footing.add(footholds[i]);
            }

            if (shared != null && !footing.isEmpty()) {
                float weight = weightSum / footing.size();
                @Nullable Vector3d slope = null;

                if (footing.size() == 2) {
                    // Two feet fix the slope along the line through them only, so the plane stays level across it
                    Vector3d across = footing.get(1).sub(footing.get(0), new Vector3d());
                    double run = across.x * across.x + across.z * across.z;

                    if (run > 1.0E-9D) slope = new Vector3d(across.x * across.y / run, across.z * across.y / run, 0.0D);
                } else if (footing.size() >= 3) {
                    // Least squares plane height = a * x + b * z + c through the feet, solved for (a, b, c)
                    double xx = 0.0D, xz = 0.0D, zz = 0.0D, x = 0.0D, z = 0.0D;
                    Vector3d heights = new Vector3d();

                    for (Vector3d foot : footing) {
                        xx += foot.x * foot.x;
                        xz += foot.x * foot.z;
                        zz += foot.z * foot.z;
                        x += foot.x;
                        z += foot.z;
                        heights.add(foot.x * foot.y, foot.z * foot.y, foot.y);
                    }

                    Matrix3d normalEquations = new Matrix3d(xx, xz, x, xz, zz, z, x, z, footing.size());

                    if (Math.abs(normalEquations.determinant()) > 1.0E-9D) slope = normalEquations.invert().transform(heights);
                }

                if (slope != null) {
                    double pitch = Math.clamp(-Math.toDegrees(Math.atan(slope.y)), -fitting.maxPitch(), fitting.maxPitch()) * weight;
                    double roll = Math.clamp(Math.toDegrees(Math.atan(slope.x)), -fitting.maxRoll(), fitting.maxRoll()) * weight;
                    Quaterniond tilt = new Quaterniond().rotationAxis(Math.toRadians(roll), 0.0D, 0.0D, 1.0D)
                        .mul(new Quaterniond().rotationAxis(Math.toRadians(pitch), 1.0D, 0.0D, 0.0D));
                    PoseOperators.rotateInModelSpace(armature, result, shared.getName(), tilt);
                }

                double lowered = Math.clamp(drop, 0.0D, fitting.maxDrop()) * weight;
                Quaterniond frame = PoseOperators.boneFrameRotation(armature, result, shared.getName());

                if (lowered > 0.0D && frame != null) {
                    Vector3d offset = frame.conjugate().transform(new Vector3d(0.0D, -lowered, 0.0D));
                    Transform transform = result.orElseIdentity(shared.getName());
                    result.putBoneTransform(shared.getName(), new Transform(transform.loc().add(offset.x, offset.y, offset.z), transform.rot(), transform.sca()));
                }
            }
        }

        for (int i = 0; i < chains.size(); i++) {
            IKChain chain = chains.get(i);
            Vector3d target = targets[i];

            if (target == null) continue;

            IKChainKinematics kinematics = IKChainKinematics.of(armature, result, chain.tipBone(), chain.chainLength(), physics);

            if (kinematics == null) continue;

            chain.solver().solve(kinematics, target);
            kinematics.settle(target, SETTLE_PASSES, SETTLE_TOLERANCE);
        }

        return result;
    }

    /// Holds the tip of the chain ending at `tipBone` at `worldTarget` in place of its placement, or lets it go with null.
    public void setTargetOverride(final String tipBone, final @Nullable Vec3 worldTarget) {
        if (worldTarget == null) {
            targetOverrides.remove(tipBone);
        } else {
            targetOverrides.put(tipBone, worldTarget);
        }
    }

    /// Where the last [#apply] placed the tip of the chain ending at `tipBone`, in the world. Null when it placed none.
    public @Nullable Vec3 getPlacedTarget(final String tipBone) {
        for (int i = 0; i < chains.size() && i < states.length; i++) {
            if (chains.get(i).tipBone().equals(tipBone)) return states[i].placed;
        }

        return null;
    }

    /// Where the animation put the tip of the chain ending at `tipBone` on the ground in the last [#apply], in the
    /// world. Null when it placed none.
    public @Nullable Vec3 getAnimatedTarget(final String tipBone) {
        for (int i = 0; i < chains.size() && i < states.length; i++) {
            if (chains.get(i).tipBone().equals(tipBone)) return states[i].animated;
        }

        return null;
    }

    /// Forgets every planted tip and step, so the chains plant afresh on the next [#apply].
    public void reset() {
        for (ChainState state : states) state.release();

        lastTime = Double.NaN;
        lastBodyPosition = null;
        bodyVelocity = Vec3.ZERO;
        bodyYawRate = 0.0D;
    }

    private static Vector3d toVector3d(final Vec3 vec) {
        return new Vector3d(vec.x(), vec.y(), vec.z());
    }

    /// Where the body stands and how it moves this frame.
    ///
    /// @param position where the armature stands in the world
    /// @param yaw      the body yaw, in degrees
    /// @param velocity the horizontal velocity, in blocks per second
    /// @param yawRate  the turn rate, in degrees per second
    private record BodyMotion(Vec3 position, double yaw, Vec3 velocity, double yawRate) {
        /// Where `point`, carried by the body, will be after `seconds` of the current motion and turn.
        private Vec3 predict(final Vec3 point, final double seconds) {
            Vec3 offset = point.subtract(position).yRot((float) -Math.toRadians(yawRate * seconds));

            return position.add(velocity.scale(seconds)).add(offset);
        }

        /// The body yaw after `seconds` of the current turn, in degrees.
        private double predictYaw(final double seconds) {
            return yaw + yawRate * seconds;
        }
    }

    /// Where one chain's tip stands and steps.
    private static final class ChainState {
        /// Where the planted tip stands in the world, null while the chain is released.
        private @Nullable Vec3 planted;

        /// The body yaw the planted tip faces, in degrees.
        private double plantedYaw;

        /// Where the running step lands in the world, null while no step runs.
        private @Nullable Vec3 stepTo;

        /// The body yaw the running step lands facing, in degrees.
        private double stepToYaw;

        /// Seconds into the running step.
        private float stepElapsed;

        /// Seconds until the next step may start.
        private float cooldown;

        /// Seconds since the last step landed or the tip was planted.
        private float idle;

        /// Whether the running step is a reset, which lands exactly where the animation puts the tip.
        private boolean resetting;

        /// Where the tip is held while the plant curve stays at 1, null while it does not.
        private @Nullable Vec3 held;

        /// Where the last [#apply] placed the tip, in the world.
        private @Nullable Vec3 placed;

        /// Where the animation put the tip on the ground in the last [#apply], in the world.
        private @Nullable Vec3 animated;

        private boolean swinging() {
            return stepTo != null;
        }

        private void release() {
            planted = null;
            stepTo = null;
            held = null;
            placed = null;
            animated = null;
            cooldown = 0.0F;
            idle = 0.0F;
        }

        /// The tip placed by the plant curve: `home` at 0, where it stood when the curve reached 1 at 1.
        private Vec3 placeByCurve(final Vec3 home, final float plant) {
            if (plant >= 1.0F - WEIGHT_EPSILON) {
                if (held == null) held = home;

                return held;
            }

            if (plant <= WEIGHT_EPSILON) {
                held = null;

                return home;
            }

            return held == null ? home : home.lerp(held, plant);
        }

        /// Plants the tip at `position` facing `yaw`, ending any step.
        private Vec3 plant(final Vec3 position, final double yaw) {
            planted = position;
            plantedYaw = yaw;
            stepTo = null;
            idle = 0.0F;

            return position;
        }

        /// Advances the steps by `frameTime` toward `home`, and answers where the tip is.
        ///
        /// A planted tip steps once it drifts past a threshold, landing as [#landing] says. While the step has to wait,
        /// for the cooldown or for the mirror chain's step, the tip slides to stay inside the thresholds instead. A tip
        /// left planted for [IKChain.Stepping#resetTime] steps back onto `home` itself once it stands past
        /// [IKChain.Stepping#resetDistance] or [IKChain.Stepping#resetAngle].
        private Vec3 step(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping, final float frameTime, final boolean mirrorSwinging) {
            cooldown = Math.max(cooldown - frameTime, 0.0F);

            if (planted == null || planted.distanceTo(home) > SNAP_DISTANCE) return plant(home, motion.yaw());

            if (stepTo != null) {
                stepElapsed += frameTime;
                float progress = Math.min(stepElapsed / (resetting ? stepping.resetStepTime() : stepping.stepTime()), 1.0F);

                if (resetting) {
                    stepTo = home;
                    stepToYaw = motion.yaw();
                } else {
                    double remaining = Math.max(stepping.stepTime() - stepElapsed, 0.0F);
                    stepTo = landing(home, motion, stepping, remaining);
                    stepToYaw = motion.predictYaw(remaining + stepping.prediction());
                }

                if (progress >= 1.0F) {
                    cooldown = stepping.stepCooldown();

                    return plant(stepTo, stepToYaw);
                }

                return planted.lerp(stepTo, progress).add(0.0D, stepping.stepHeight() * Mth.sin(progress * Mth.PI), 0.0D);
            }

            idle += frameTime;

            double turn = Mth.wrapDegrees(motion.yaw() - plantedYaw);
            boolean free = cooldown <= 0.0F && !mirrorSwinging;

            if (planted.distanceTo(home) <= stepping.distanceThreshold() && Math.abs(turn) <= stepping.angularThreshold()) {
                boolean offBounds = planted.distanceTo(home) > stepping.resetDistance() || Math.abs(turn) > stepping.resetAngle();

                if (free && idle >= stepping.resetTime() && offBounds) startStep(home, motion.yaw(), true);

                return planted;
            }

            if (free) {
                startStep(
                    landing(home, motion, stepping, stepping.stepTime()), motion.predictYaw(stepping.stepTime() + stepping.prediction()), false
                );

                return planted;
            }

            Vec3 drift = planted.subtract(home);

            if (drift.length() > stepping.distanceThreshold()) planted = home.add(drift.normalize().scale(stepping.distanceThreshold()));

            plantedYaw = motion.yaw() - Math.clamp(turn, -stepping.angularThreshold(), stepping.angularThreshold());

            return planted;
        }

        private void startStep(final Vec3 to, final double yaw, final boolean reset) {
            stepTo = to;
            stepToYaw = yaw;
            stepElapsed = 0.0F;
            resetting = reset;
        }

        /// Where a step with `remaining` seconds left lands: where `home` will be when the step ends, carried on by the
        /// body for [IKChain.Stepping#prediction] seconds more, but at most [IKChain.Stepping#distanceThreshold] past it.
        private static Vec3 landing(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping, final double remaining) {
            Vec3 arrival = motion.predict(home, remaining);
            Vec3 overshoot = motion.predict(home, remaining + stepping.prediction()).subtract(arrival);

            if (overshoot.length() > stepping.distanceThreshold()) overshoot = overshoot.normalize().scale(stepping.distanceThreshold());

            return arrival.add(overshoot);
        }
    }
}
