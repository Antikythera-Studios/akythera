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

    /// How far beside a tip the ground is sampled for its slope, in blocks.
    private static final double SLOPE_PROBE = 0.25D;

    /// How far above and below a tip the slope samples search for ground, in blocks.
    private static final double SLOPE_SEARCH = 0.5D;

    /// How far a planted tip may end up from where the animation puts it before it plants there at once instead of
    /// stepping, in blocks. Covers a teleport.
    private static final double SNAP_DISTANCE = 4.0D;

    /// The chains [#states] were made for.
    private List<IKChain> chains = List.of();

    /// The state of each of [#chains], in its order.
    private ChainState[] states = new ChainState[0];

    /// World targets that hold chain tips in place of their placement, by the chain's tip bone.
    private final Map<String, Vec3> targetOverrides = new HashMap<>();

    /// When the last [#apply] ran, in seconds; NaN before the first.
    private double lastTime = Double.NaN;

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
        Quaterniond worldToArmature = armatureToWorld.rot().conjugate(new Quaterniond());

        @Nullable Vector3d[] targets = new Vector3d[chains.size()];
        @Nullable Vector3d[] normals = new Vector3d[chains.size()];
        @Nullable Bone[] chainRoots = new Bone[chains.size()];
        float[] weights = new float[chains.size()];
        boolean[] grounded = new boolean[chains.size()];
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
                    ? state.step(home, bodyYaw, stepping, frameTime, mirrorSwinging)
                    : state.plant(byCurve, bodyYaw);
                placed = animatedTip.lerp(byCurve.lerp(stepped, channel.procedural()), channel.alpha());
                weights[i] = channel.alpha();
            }

            state.placed = placed;
            grounded[i] = ground.isPresent() || override != null;
            targets[i] = toVector3d(armatureToWorld.inverseMul(placed));

            if (chain.alignToNormal() && ground.isPresent()) {
                OptionalDouble center = terrain.groundHeight(placed.x(), placed.z(), placed.y() + SLOPE_SEARCH, placed.y() - SLOPE_SEARCH);
                OptionalDouble alongX = terrain.groundHeight(placed.x() + SLOPE_PROBE, placed.z(), placed.y() + SLOPE_SEARCH, placed.y() - SLOPE_SEARCH);
                OptionalDouble alongZ = terrain.groundHeight(placed.x(), placed.z() + SLOPE_PROBE, placed.y() + SLOPE_SEARCH, placed.y() - SLOPE_SEARCH);

                if (center.isPresent() && alongX.isPresent() && alongZ.isPresent()) {
                    Vector3d normal = new Vector3d(
                        center.getAsDouble() - alongX.getAsDouble(), SLOPE_PROBE, center.getAsDouble() - alongZ.getAsDouble()
                    );
                    normals[i] = worldToArmature.transform(normal).normalize();
                }
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

                if (chainRoot == null || target == null || !grounded[i] || chainRoot.getParent() == null) continue;

                Set<Bone> lineage = new HashSet<>();

                for (Bone ancestor = chainRoot.getParent(); ancestor != null; ancestor = ancestor.getParent()) lineage.add(ancestor);

                if (shared == null) {
                    shared = chainRoot.getParent();
                } else {
                    while (shared != null && !lineage.contains(shared)) shared = shared.getParent();
                }

                Vector3d hip = toVector3d(VectorUtils.toVec3(bound[chainRoot.getId()].getTranslation(new Vector3f())));
                double horizontal = Math.hypot(target.x - hip.x, target.z - hip.z);
                double rise = horizontal < reaches[i] ? Math.sqrt(reaches[i] * reaches[i] - horizontal * horizontal) : 0.0D;
                drop = Math.max(drop, hip.y - target.y - rise);
                weightSum += weights[i];
                footing.add(target);
            }

            if (shared != null && !footing.isEmpty()) {
                float weight = weightSum / footing.size();

                if (footing.size() >= 3) {
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

                    if (Math.abs(normalEquations.determinant()) > 1.0E-9D) {
                        Vector3d slope = normalEquations.invert().transform(heights);
                        double pitch = Math.clamp(-Math.toDegrees(Math.atan(slope.y)), -fitting.maxPitch(), fitting.maxPitch()) * weight;
                        double roll = Math.clamp(Math.toDegrees(Math.atan(slope.x)), -fitting.maxRoll(), fitting.maxRoll()) * weight;
                        Quaterniond tilt = new Quaterniond().rotationAxis(Math.toRadians(roll), 0.0D, 0.0D, 1.0D)
                            .mul(new Quaterniond().rotationAxis(Math.toRadians(pitch), 1.0D, 0.0D, 0.0D));
                        PoseOperators.rotateInModelSpace(armature, result, shared.getName(), tilt);
                    }
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

            Bone tip = armature.getBoneByName(chain.tipBone());
            Vector3d normal = normals[i];
            List<Bone> feet = normal == null || tip == null ? List.of() : tip.getChildBones();
            List<Quaterniond> footRotations = new ArrayList<>();

            for (Bone foot : feet) footRotations.add(modelRotation(armature, result, foot));

            chain.solver().solve(kinematics, target);

            if (normal == null) continue;

            Quaterniond slope = new Quaterniond().slerp(new Quaterniond().rotationTo(0.0D, 1.0D, 0.0D, normal.x, normal.y, normal.z), weights[i]);

            for (int k = 0; k < feet.size(); k++) {
                Quaterniond solved = modelRotation(armature, result, feet.get(k));
                Quaterniond turn = new Quaterniond(slope).mul(footRotations.get(k)).mul(solved.conjugate());
                PoseOperators.rotateInModelSpace(armature, result, feet.get(k).getName(), turn);
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

    /// Forgets every planted tip and step, so the chains plant afresh on the next [#apply].
    public void reset() {
        for (ChainState state : states) state.release();

        lastTime = Double.NaN;
    }

    /// `bone`'s rotation in armature space under `pose`.
    private static Quaterniond modelRotation(final Armature armature, final Pose pose, final Bone bone) {
        Quaterniond frame = PoseOperators.boneFrameRotation(armature, pose, bone.getName());

        return frame == null ? new Quaterniond() : frame.mul(pose.orElseIdentity(bone.getName()).rot());
    }

    private static Vector3d toVector3d(final Vec3 vec) {
        return new Vector3d(vec.x(), vec.y(), vec.z());
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

        /// Where the tip is held while the plant curve stays at 1, null while it does not.
        private @Nullable Vec3 held;

        /// Where the last [#apply] placed the tip, in the world.
        private @Nullable Vec3 placed;

        private boolean swinging() {
            return stepTo != null;
        }

        private void release() {
            planted = null;
            stepTo = null;
            held = null;
            placed = null;
            cooldown = 0.0F;
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

            return position;
        }

        /// Advances the steps by `frameTime` toward `home`, and answers where the tip is.
        ///
        /// A planted tip steps once it drifts past a threshold. While the step has to wait, for the cooldown or for
        /// the mirror chain's step, the tip slides to stay inside the thresholds instead.
        private Vec3 step(final Vec3 home, final double bodyYaw, final IKChain.Stepping stepping, final float frameTime, final boolean mirrorSwinging) {
            cooldown = Math.max(cooldown - frameTime, 0.0F);

            if (planted == null || planted.distanceTo(home) > SNAP_DISTANCE) return plant(home, bodyYaw);

            if (stepTo != null) {
                stepElapsed += frameTime;
                float progress = Math.min(stepElapsed / stepping.stepTime(), 1.0F);

                if (progress >= 1.0F) {
                    cooldown = stepping.stepCooldown();

                    return plant(stepTo, stepToYaw);
                }

                return planted.lerp(stepTo, progress).add(0.0D, stepping.stepHeight() * Mth.sin(progress * Mth.PI), 0.0D);
            }

            double turn = Mth.wrapDegrees(bodyYaw - plantedYaw);

            if (planted.distanceTo(home) <= stepping.distanceThreshold() && Math.abs(turn) <= stepping.angularThreshold()) return planted;

            if (cooldown <= 0.0F && !mirrorSwinging) {
                stepTo = home;
                stepToYaw = bodyYaw;
                stepElapsed = 0.0F;

                return planted;
            }

            Vec3 drift = planted.subtract(home);

            if (drift.length() > stepping.distanceThreshold()) planted = home.add(drift.normalize().scale(stepping.distanceThreshold()));

            plantedYaw = bodyYaw - Math.clamp(turn, -stepping.angularThreshold(), stepping.angularThreshold());

            return planted;
        }
    }
}
