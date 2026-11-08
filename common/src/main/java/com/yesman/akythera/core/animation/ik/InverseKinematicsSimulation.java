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

/// Places the [IKChain]s of one armature each frame: turns the shown body after the body yaw under
/// [ArmaturePhysicsData.BodyTurn], steps the chains over the ground in their gait groups, follows the [IKCurves] of the
/// playing animation, fits the chains' shared parent to the feet, and solves each chain. The [IKChannels] of the pose
/// decide how much of each happens.
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

    /// Part of its thresholds a gait group steps at right after another group's step started.
    private static final double GAIT_FOLLOW = 0.5D;

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

    /// The shown body's turn rate, in degrees per second, smoothed over [#MOTION_SMOOTHING].
    private double bodyYawRate;

    /// How far the last [#apply] turned the shown body from the body yaw, in degrees.
    private float lastShownTurn;

    /// The gait group that started the last group step, empty before the first.
    private String lastGaitGroup = "";

    /// When the last group step started, in seconds on the clock of [#apply].
    private double lastGaitStart = Double.NEGATIVE_INFINITY;

    private final BodyTurner bodyTurner = new BodyTurner();

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
        Vec3 forward = armatureToWorld.mulDirection(MathUtils.ZP);
        double bodyYaw = Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        double floorHeight = armatureToWorld.loc().y();
        Vec3 bodyPosition = armatureToWorld.loc();
        boolean continued = lastBodyPosition != null && frameTime > 0.0F && bodyPosition.distanceTo(lastBodyPosition) <= SNAP_DISTANCE;
        double blend = 1.0D - Math.exp(-frameTime / MOTION_SMOOTHING);
        float frameTurn = continued ? (float) Mth.wrapDegrees(bodyYaw - lastBodyYaw) : 0.0F;

        if (continued) {
            Vec3 moved = bodyPosition.subtract(lastBodyPosition);
            bodyVelocity = bodyVelocity.lerp(new Vec3(moved.x(), 0.0D, moved.z()).scale(1.0D / frameTime), blend);
        } else if (lastBodyPosition != null && frameTime > 0.0F) {
            bodyVelocity = Vec3.ZERO;
            bodyYawRate = 0.0D;
            bodyTurner.reset();
        }

        lastBodyPosition = bodyPosition;
        lastBodyYaw = bodyYaw;

        ArmaturePhysicsData.BodyTurn bodyTurn = physics.bodyTurn();
        float shownTurn = 0.0F;

        if (bodyTurn.enabled()) {
            float alpha = 0.0F;
            float stepped = 0.0F;

            for (IKChain chain : chains) {
                IKChannel channel = channels == null ? IKChannel.NONE : channels.get(chain.tipBone());
                alpha += channel.alpha();
                stepped += channel.alpha() * channel.procedural();
            }

            bodyTurner.update(bodyTurn, Mth.lerp(stepped / chains.size(), bodyTurn.maxTurnSpeed(), bodyTurn.maxStepTurnSpeed()), frameTurn, frameTime);
            shownTurn = bodyTurner.lag() * alpha / chains.size();
        }

        if (continued) bodyYawRate = Mth.lerp(blend, bodyYawRate, (frameTurn - shownTurn + lastShownTurn) / frameTime);

        lastShownTurn = shownTurn;

        Pose turned = pose;

        if (shownTurn != 0.0F) {
            turned = new Pose(pose);
            PoseOperators.rotateInModelSpace(armature, turned, armature.getRootBone().getName(), new Quaterniond().rotateY(Math.toRadians(shownTurn)));

            for (String steadyBone : bodyTurn.steadyBones()) {
                PoseOperators.rotateInModelSpace(armature, turned, steadyBone, new Quaterniond().rotateY(Math.toRadians(-shownTurn)));
            }
        }

        Matrix4f[] bound = turned.getForwardBoundTransforms(armatureAccessor);
        BodyMotion motion = new BodyMotion(bodyPosition, bodyYaw - shownTurn, bodyVelocity, bodyYawRate);

        @Nullable Vector3d[] targets = new Vector3d[chains.size()];
        @Nullable Bone[] chainRoots = new Bone[chains.size()];
        float[] weights = new float[chains.size()];
        @Nullable Vector3d[] footholds = new Vector3d[chains.size()];
        double[] reaches = new double[chains.size()];
        @Nullable Vec3[] homes = new Vec3[chains.size()];
        @Nullable Vec3[] animatedTips = new Vec3[chains.size()];
        @Nullable Vec3[] byCurves = new Vec3[chains.size()];
        boolean[] stepping = new boolean[chains.size()];
        double[] facingTurns = new double[chains.size()];

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

            Vec3 animatedTip = armatureToWorld.mul(VectorUtils.toVec3(bound[tip.getId()].transformPosition(new Vector3f(tip.getToTail()))));
            double rootHeight = armatureToWorld.mul(VectorUtils.toVec3(bound[chainRoot.getId()].getTranslation(new Vector3f()))).y();
            OptionalDouble ground = terrain.groundHeight(
                animatedTip.x(), animatedTip.z(), Math.max(rootHeight, animatedTip.y()), animatedTip.y() - chain.stepping().rayMaxDistance()
            );
            if (override == null && ground.isEmpty()) {
                state.release();
                continue;
            }

            Vec3 home = ground.isPresent()
                ? new Vec3(animatedTip.x(), ground.getAsDouble() + animatedTip.y() - floorHeight, animatedTip.z())
                : animatedTip;

            homes[i] = home;
            animatedTips[i] = animatedTip;

            if (override != null) {
                footholds[i] = toVector3d(armatureToWorld.inverseMul(override));
            } else if (ground.isPresent()) {
                footholds[i] = toVector3d(armatureToWorld.inverseMul(new Vec3(animatedTip.x(), ground.getAsDouble(), animatedTip.z())));
            }

            if (override != null) continue;

            byCurves[i] = state.placeByCurve(
                home, ground.isPresent() ? animatedTip.y() - floorHeight : 0.0D, channel.plant(), motion.yaw(), chain.stepping(), frameTime
            );

            if (channel.procedural() <= WEIGHT_EPSILON) {
                state.plant(byCurves[i], motion.yaw());
            } else {
                state.prepare(home, motion, chain.stepping(), frameTime);
                stepping[i] = true;
            }
        }

        // Ungrouped tips step on their own while their mirror tip is planted
        for (int i = 0; i < chains.size(); i++) {
            IKChain.Stepping settings = chains.get(i).stepping();
            String mirrorTip = armature.getMirrorBone(chains.get(i).tipBone());
            boolean mirrorSwinging = false;

            if (!stepping[i] || !settings.gaitGroup().isEmpty() || !states[i].ready()) continue;

            for (int j = 0; j < chains.size(); j++) {
                if (j != i && chains.get(j).tipBone().equals(mirrorTip) && states[j].swinging()) mirrorSwinging = true;
            }

            if (mirrorSwinging) continue;

            if (states[i].urgency(homes[i], motion, settings) > 1.0D) {
                states[i].start(homes[i], motion, settings, false);
            } else if (states[i].wantsReset(homes[i], motion, settings)) {
                states[i].start(homes[i], motion, settings, true);
            }
        }

        // A gait group steps all its tips at once, only while no other group swings
        Map<String, List<Integer>> gaitGroups = new HashMap<>();
        boolean groupSwinging = false;

        for (int i = 0; i < chains.size(); i++) {
            String group = chains.get(i).stepping().gaitGroup();

            if (!stepping[i] || group.isEmpty()) continue;

            gaitGroups.computeIfAbsent(group, _ -> new ArrayList<>()).add(i);
            groupSwinging |= states[i].swinging();
        }

        String startingGroup = null;
        boolean startingWaited = false;
        boolean startingLeads = false;
        double startingUrgency = 0.0D;
        boolean startingReset = false;
        IKChain.TurnLead turnLead = motion.yawRate() > 0.0D ? IKChain.TurnLead.RIGHT : IKChain.TurnLead.LEFT;

        if (groupSwinging) gaitGroups.clear();

        // The group that stepped last yields, then the one leading the turn goes first
        for (Map.Entry<String, List<Integer>> group : gaitGroups.entrySet()) {
            double urgency = 0.0D;
            boolean reset = false;
            boolean ready = true;
            boolean leads = false;
            boolean waited = !group.getKey().equals(lastGaitGroup);
            double due = waited && time - lastGaitStart < 2.0D * chains.get(group.getValue().getFirst()).stepping().stepTime() ? GAIT_FOLLOW : 1.0D;

            for (int i : group.getValue()) {
                ready &= states[i].ready();
                urgency = Math.max(urgency, states[i].urgency(homes[i], motion, chains.get(i).stepping()));
                reset |= states[i].wantsReset(homes[i], motion, chains.get(i).stepping());
                leads |= chains.get(i).stepping().turnLead() == turnLead;
            }

            if (!ready || urgency <= due && !reset) continue;

            boolean better = startingGroup == null
                || waited && !startingWaited
                || waited == startingWaited && leads && !startingLeads
                || waited == startingWaited && leads == startingLeads && urgency > startingUrgency;

            if (better) {
                startingGroup = group.getKey();
                startingWaited = waited;
                startingLeads = leads;
                startingUrgency = urgency;
                startingReset = urgency <= due;
            }
        }

        if (startingGroup != null) {
            for (int i : gaitGroups.get(startingGroup)) states[i].start(homes[i], motion, chains.get(i).stepping(), startingReset);

            lastGaitGroup = startingGroup;
            lastGaitStart = time;
        }

        for (int i = 0; i < chains.size(); i++) {
            IKChain chain = chains.get(i);
            ChainState state = states[i];
            Vec3 home = homes[i];
            Vec3 override = targetOverrides.get(chain.tipBone());
            IKChannel channel = channels == null ? IKChannel.NONE : channels.get(chain.tipBone());
            Vec3 placed;

            if (home == null) continue;

            if (override != null) {
                placed = override;
                weights[i] = 1.0F;
            } else {
                Vec3 stepped = stepping[i] ? state.position(home, motion, chain.stepping()) : state.planted;
                placed = animatedTips[i].lerp(byCurves[i].lerp(stepped, channel.procedural()), channel.alpha());
                weights[i] = channel.alpha();

                if (chain.stepping().holdFacing()) facingTurns[i] = state.facingTurn(motion, channel, chain.stepping());
            }

            state.placed = placed;
            state.animated = home;
            targets[i] = toVector3d(armatureToWorld.inverseMul(placed));
        }

        Pose result = new Pose(turned);

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

            if (facingTurns[i] != 0.0D) {
                Vector3d up = kinematics.joint(0).sub(kinematics.joint(kinematics.length()), new Vector3d()).normalize();
                kinematics.rotate(0, new Quaterniond().rotationAxis(Math.toRadians(-facingTurns[i]), up.x, up.y, up.z));
            }
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

        bodyTurner.reset();
        lastShownTurn = 0.0F;

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

        /// The body yaw the planted tip faced when it landed or the running step lifted it, in degrees.
        private double facingYaw;

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

        /// The ground point the tip is held on while the plant curve stays at 1, null while it does not.
        private @Nullable Vec3 held;

        /// The body yaw the held tip faces, in degrees.
        private double heldYaw;

        /// Where the held tip let go from, null while none catches up.
        private @Nullable Vec3 letGo;

        /// Seconds since the held tip let go.
        private float letGoElapsed;

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
            letGo = null;
            placed = null;
            animated = null;
            cooldown = 0.0F;
            idle = 0.0F;
        }

        /// The tip placed by the plant curve: `home` at 0, the held ground point raised by `lift` at 1. A held tip that
        /// drifts past [IKChain.Stepping#distanceThreshold] steps back onto `home`.
        private Vec3 placeByCurve(
            final Vec3 home, final double lift, final float plant, final double yaw, final IKChain.Stepping stepping, final float frameTime
        ) {
            if (letGo != null) {
                letGoElapsed += frameTime;
                float progress = Math.min(letGoElapsed / stepping.stepTime(), 1.0F);

                if (progress < 1.0F) {
                    return letGo.lerp(home, Mth.smoothstep(progress)).add(0.0D, stepping.stepHeight() * Mth.sin(progress * Mth.PI), 0.0D);
                }

                letGo = null;
                placed = home;
            }

            if (plant >= 1.0F - WEIGHT_EPSILON) {
                if (held == null) {
                    held = (placed == null ? home : placed).subtract(0.0D, lift, 0.0D);
                    heldYaw = yaw;
                }

                Vec3 standing = held.add(0.0D, lift, 0.0D);

                if (standing.distanceTo(home) > stepping.distanceThreshold()) {
                    letGo = standing;
                    letGoElapsed = 0.0F;
                    held = null;
                }

                return standing;
            }

            if (plant <= WEIGHT_EPSILON) {
                held = null;

                return home;
            }

            if (held == null) return home;

            Vec3 blended = home.lerp(held.add(0.0D, lift, 0.0D), plant);

            if (held.add(0.0D, lift, 0.0D).distanceTo(home) > stepping.distanceThreshold()) {
                letGo = blended;
                letGoElapsed = 0.0F;
                held = null;
            }

            return blended;
        }

        /// How far the tip faces away from the shown body, in degrees, at most [IKChain.Stepping#angularThreshold].
        private double facingTurn(final BodyMotion motion, final IKChannel channel, final IKChain.Stepping stepping) {
            double stepped = 0.0D;

            if (planted != null) {
                stepped = Mth.wrapDegrees(facingYaw - motion.yaw());

                if (stepTo != null) stepped *= 1.0F - Math.min(stepElapsed / (resetting ? stepping.resetStepTime() : stepping.stepTime()), 1.0F);
            }

            double byCurve = held == null ? 0.0D : Mth.wrapDegrees(heldYaw - motion.yaw()) * channel.plant();
            double turn = Mth.lerp(channel.procedural(), byCurve, stepped) * channel.alpha();

            return Math.clamp(turn, -stepping.angularThreshold(), stepping.angularThreshold());
        }

        /// Plants the tip at `position` facing `yaw`, ending any step.
        private Vec3 plant(final Vec3 position, final double yaw) {
            planted = position;
            plantedYaw = yaw;
            facingYaw = yaw;
            stepTo = null;
            idle = 0.0F;

            return position;
        }

        /// Plants an unplanted or far tip at `home`, and moves a running step on by `frameTime`, landing it at its end.
        private void prepare(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping, final float frameTime) {
            cooldown = Math.max(cooldown - frameTime, 0.0F);

            if (planted == null || planted.distanceTo(home) > SNAP_DISTANCE) {
                plant(home, motion.yaw());

                return;
            }

            if (stepTo == null) {
                idle += frameTime;

                return;
            }

            stepElapsed += frameTime;

            if (resetting) {
                stepTo = home;
                stepToYaw = motion.yaw();
            } else {
                double remaining = Math.max(stepping.stepTime() - stepElapsed, 0.0F);
                stepTo = landing(home, motion, stepping, remaining);
                stepToYaw = motion.predictYaw(remaining + stepping.prediction());
            }

            if (stepElapsed >= (resetting ? stepping.resetStepTime() : stepping.stepTime())) {
                cooldown = stepping.stepCooldown();
                plant(stepTo, stepToYaw);
                facingYaw = motion.yaw();
            }
        }

        /// Whether the tip stands planted and may start a step.
        private boolean ready() {
            return planted != null && stepTo == null && cooldown <= 0.0F;
        }

        /// How far past its thresholds the planted tip stands: over 1 once it should step, 0 while it swings.
        private double urgency(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping) {
            if (planted == null || stepTo != null) return 0.0D;

            double turn = Math.abs(Mth.wrapDegrees(motion.yaw() - plantedYaw));

            return Math.max(
                planted.distanceTo(home) / Math.max(stepping.distanceThreshold(), MathUtils.EPSILON),
                turn / Math.max(stepping.angularThreshold(), MathUtils.EPSILON)
            );
        }

        /// Whether the tip, planted for [IKChain.Stepping#resetTime], stands off its reset distance or angle.
        private boolean wantsReset(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping) {
            if (planted == null || stepTo != null || idle < stepping.resetTime()) return false;

            return planted.distanceTo(home) > stepping.resetDistance() || Math.abs(Mth.wrapDegrees(motion.yaw() - plantedYaw)) > stepping.resetAngle();
        }

        /// Starts a step: a reset lands exactly on `home`, any other step where [#landing] says.
        private void start(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping, final boolean reset) {
            stepTo = reset ? home : landing(home, motion, stepping, stepping.stepTime());
            stepToYaw = reset ? motion.yaw() : motion.predictYaw(stepping.stepTime() + stepping.prediction());
            stepElapsed = 0.0F;
            resetting = reset;
        }

        /// Where the tip is: planted, or on its way through a running step. A planted tip that must wait slides to stay
        /// inside the thresholds.
        private Vec3 position(final Vec3 home, final BodyMotion motion, final IKChain.Stepping stepping) {
            if (stepTo != null) {
                float progress = Math.min(stepElapsed / (resetting ? stepping.resetStepTime() : stepping.stepTime()), 1.0F);
                double lift = Math.min(planted.distanceTo(stepTo) / Math.max(stepping.distanceThreshold(), MathUtils.EPSILON), 1.0D);

                return planted.lerp(stepTo, progress).add(0.0D, stepping.stepHeight() * lift * Mth.sin(progress * Mth.PI), 0.0D);
            }

            double turn = Mth.wrapDegrees(motion.yaw() - plantedYaw);
            Vec3 drift = planted.subtract(home);

            if (drift.length() > stepping.distanceThreshold()) planted = home.add(drift.normalize().scale(stepping.distanceThreshold()));

            plantedYaw = motion.yaw() - Math.clamp(turn, -stepping.angularThreshold(), stepping.angularThreshold());

            return planted;
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
