package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/// Turns the body in steps instead of spinning it in place. A yaw change at full weight starts a step: the model
/// keeps the yaw it showed and catches up by the step's end, while each [ArmatureLoader.Leg] keeps its foot planted
/// and steps over to the step's yaw. Legs that lead the turn step in the first half, the others in the second half.
/// A step covers [#MAX_STEP] degrees at most, so steps follow one another until the body and feet reach the yaw. The
/// body lowers as far as the planted feet need to stay on the ground.
public final class TurnInPlaceDriver extends AbstractWrapperDriver {
    /// Largest turn one step covers, in degrees.
    private static final float MAX_STEP = 60.0F;

    /// Farthest the body stays behind the yaw, in degrees.
    private static final float MAX_LAG = 180.0F;

    /// Lag a finished step may leave without another step, in degrees.
    private static final float SETTLE_TURN = 0.5F;

    /// Farthest a planted foot stays behind or ahead of the body, in degrees.
    private static final float MAX_FOOT_TWIST = 40.0F;

    /// Weight a yaw change needs to start a step.
    private static final float STEP_START_WEIGHT = 0.98F;

    /// Turn at which a foot lifts to the leg's full step height, in degrees.
    private static final float FULL_LIFT_TURN = 30.0F;

    /// Part of the step in which a leading foot moves, as fractions of the step.
    private static final float LEAD_START = 0.15F;
    private static final float LEAD_END = 0.52F;

    /// Part of the step in which a following foot moves, as fractions of the step.
    private static final float FOLLOW_START = 0.6F;
    private static final float FOLLOW_END = 0.93F;

    /// Farthest the body lowers for the feet, in armature units.
    private static final double MAX_DROP = 0.1D;

    /// Distance kept between a leg's reach and its full length, in armature units.
    private static final double REACH_MARGIN = 1.0E-3D;

    private final Animator animator;
    private final Supplier<Float> yawUpdater;
    private final Supplier<Float> weightUpdater;

    /// Length of a step, in seconds.
    private final float stepTime;

    /// The yaw of the last update, in degrees. NaN until the first update.
    private float yaw = Float.NaN;

    /// How far the yaw the running step started from is behind the current yaw, in degrees.
    private float originLag;

    /// How far the shown body is behind the current yaw, in degrees.
    private float bodyLag;

    /// How far each leg's planted foot is behind the current yaw, in degrees.
    private float[] plantedLags = new float[0];

    /// How far the yaw each leg steps to is behind the current yaw, in degrees.
    private float[] goalLags = new float[0];

    /// Seconds into the running step, negative while no step runs.
    private float stepElapsed = -1.0F;

    /// Whether the running step turns toward a larger yaw.
    private boolean turnsRight;

    private float weight;

    /// Model space bone matrices of the pose before the turn, rewritten each frame.
    private Matrix4f[] restMatrices = new Matrix4f[0];

    /// Model space bone matrices of the turned pose, rewritten each frame.
    private Matrix4f[] turnedMatrices = new Matrix4f[0];

    /// @param yawUpdater The body yaw the renderer turns the model by, in degrees
    /// @param weightUpdater How much of the turn is shown, in [0, 1]. 0 while the entity walks
    /// @param stepTime Length of a step, in seconds
    public TurnInPlaceDriver(
        final Animator animator, final AnimationDriver base, final Supplier<Float> yawUpdater,
        final Supplier<Float> weightUpdater, final float stepTime
    ) {
        super(base);

        this.animator = animator;
        this.yawUpdater = yawUpdater;
        this.weightUpdater = weightUpdater;
        this.stepTime = Math.max(stepTime, 0.05F);
    }

    @Override
    public void start() {
        super.start();

        yaw = Float.NaN;
        settle();
    }

    @Override
    public void update(final float frameTime) {
        super.update(frameTime);

        float newYaw = yawUpdater.get();
        float frameTurn = Float.isNaN(yaw) ? 0.0F : Mth.wrapDegrees(newYaw - yaw);
        List<ArmatureLoader.Leg> legs = animator.getArmature().get().getLegs();
        yaw = newYaw;
        weight = Math.clamp(weightUpdater.get(), 0.0F, 1.0F);

        if (plantedLags.length != legs.size()) {
            plantedLags = new float[legs.size()];
            goalLags = new float[legs.size()];
            settle();
        }

        if (stepElapsed < 0.0F) {
            if (weight < STEP_START_WEIGHT || frameTurn == 0.0F) {
                return;
            }

            stepElapsed = 0.0F;
            turnsRight = frameTurn > 0.0F;
        } else if (weight <= 0.0F) {
            settle();

            return;
        } else {
            stepElapsed += frameTime;
        }

        originLag += frameTurn;

        for (int i = 0; i < legs.size(); i++) {
            plantedLags[i] += frameTurn;
            goalLags[i] += frameTurn;
        }

        float progress = Math.min(stepElapsed / stepTime, 1.0F);
        float goalLag = originLag - Math.clamp(originLag, -MAX_STEP, MAX_STEP);

        for (int i = 0; i < legs.size(); i++) {
            if (legProgress(legs.get(i), progress) < 1.0F) {
                goalLags[i] = goalLag;
            }
        }

        if (stepElapsed >= stepTime) {
            float largest = Math.abs(goalLag);
            float total = goalLag;
            originLag = goalLag;

            for (int i = 0; i < legs.size(); i++) {
                plantedLags[i] = goalLags[i];
                largest = Math.max(largest, Math.abs(goalLags[i]));
                total += goalLags[i];
            }

            if (largest <= SETTLE_TURN) {
                settle();

                return;
            }

            stepElapsed = 0.0F;
            progress = 0.0F;
            turnsRight = total > 0.0F;
        }

        bodyLag = originLag - Math.clamp(originLag, -MAX_STEP, MAX_STEP) * (float)Mth.smoothstep(progress);
        float excess = bodyLag - Math.clamp(bodyLag, -MAX_LAG, MAX_LAG);

        if (excess != 0.0F) {
            originLag -= excess;
            bodyLag -= excess;

            for (int i = 0; i < legs.size(); i++) {
                plantedLags[i] -= excess;
                goalLags[i] -= excess;
            }
        }
    }

    @Override
    public Pose getPose() {
        Pose pose = base.getPose();
        AssetAccessor<? extends Armature> armatureAccessor = animator.getArmature();
        Armature armature = armatureAccessor.get();
        List<ArmatureLoader.Leg> legs = armature.getLegs();

        if (stepElapsed < 0.0F || weight <= 0.0F || plantedLags.length != legs.size()) {
            return pose;
        }

        float progress = Math.min(stepElapsed / stepTime, 1.0F);
        float bodyTurn = bodyLag * weight;

        if (restMatrices.length != armature.getBoneCount()) {
            restMatrices = Matrix4fUtils.allocateMatrixArray(armature.getBoneCount());
            turnedMatrices = Matrix4fUtils.allocateMatrixArray(armature.getBoneCount());
        }

        pose.getForwardBoundTransforms(armatureAccessor, restMatrices);

        Pose result = new Pose(pose);
        PoseOperators.rotateInModelSpace(armature, result, ArmatureLoader.ROOT_BONE, yawRotation(bodyTurn));
        result.getForwardBoundTransforms(armatureAccessor, turnedMatrices);

        Vector3d[] targets = new Vector3d[legs.size()];
        double drop = 0.0D;

        for (int i = 0; i < legs.size(); i++) {
            ArmatureLoader.Leg leg = legs.get(i);
            Bone thigh = armature.getBoneByName(leg.thigh());
            Bone shin = armature.getBoneByName(leg.shin());

            if (thigh == null || shin == null) {
                continue;
            }

            float stepProgress = legProgress(leg, progress);
            float footLag = plantedLags[i] + (goalLags[i] - plantedLags[i]) * (float)Mth.smoothstep(stepProgress);
            float footTurn = bodyTurn + Math.clamp(footLag * weight - bodyTurn, -MAX_FOOT_TWIST, MAX_FOOT_TWIST);
            float lift = Math.min(Math.abs(goalLags[i] - plantedLags[i]) * weight / FULL_LIFT_TURN, 1.0F);

            Vector3d restFoot = new Vector3d(restMatrices[shin.getId()].transformPosition(new Vector3f(shin.getToTail())));
            targets[i] = yawRotation(footTurn).transform(restFoot)
                .add(0.0D, leg.stepHeight() * lift * Mth.sin(stepProgress * Mth.PI), 0.0D);

            Vector3d hip = new Vector3d(turnedMatrices[thigh.getId()].getTranslation(new Vector3f()));
            Vector3d knee = new Vector3d(turnedMatrices[shin.getId()].getTranslation(new Vector3f()));
            Vector3d foot = new Vector3d(turnedMatrices[shin.getId()].transformPosition(new Vector3f(shin.getToTail())));
            double reach = knee.distance(hip) + foot.distance(knee) - REACH_MARGIN;
            double horizontal = Math.hypot(targets[i].x - hip.x, targets[i].z - hip.z);

            if (horizontal < reach) {
                drop = Math.max(drop, hip.y - targets[i].y - Math.sqrt(reach * reach - horizontal * horizontal));
            }
        }

        drop = Math.min(drop, MAX_DROP);

        if (drop > 0.0D) {
            Quaterniond rootFrame = PoseOperators.boneFrameRotation(armature, result, ArmatureLoader.ROOT_BONE);
            Transform root = result.orElseIdentity(ArmatureLoader.ROOT_BONE);
            Vector3d offset = rootFrame.conjugate().transform(new Vector3d(0.0D, -drop, 0.0D));
            result.putBoneTransform(ArmatureLoader.ROOT_BONE, new Transform(root.loc().add(offset.x, offset.y, offset.z), root.rot(), root.sca()));
        }

        for (int i = 0; i < legs.size(); i++) {
            ArmatureLoader.Leg leg = legs.get(i);
            Bone thigh = armature.getBoneByName(leg.thigh());
            Bone shin = armature.getBoneByName(leg.shin());

            if (thigh == null || shin == null) {
                continue;
            }

            Vector3d hip = new Vector3d(turnedMatrices[thigh.getId()].getTranslation(new Vector3f())).sub(0.0D, drop, 0.0D);
            Vector3d knee = new Vector3d(turnedMatrices[shin.getId()].getTranslation(new Vector3f())).sub(0.0D, drop, 0.0D);
            Vector3d foot = new Vector3d(turnedMatrices[shin.getId()].transformPosition(new Vector3f(shin.getToTail()))).sub(0.0D, drop, 0.0D);
            Vector3d thighVector = knee.sub(hip, new Vector3d());
            Vector3d shinVector = foot.sub(knee, new Vector3d());
            Vector3d toTarget = targets[i].sub(hip, new Vector3d());
            double thighLength = thighVector.length();
            double shinLength = shinVector.length();
            double reach = Math.clamp(toTarget.length(), Math.abs(thighLength - shinLength) + REACH_MARGIN, thighLength + shinLength - REACH_MARGIN);

            Vector3d direction = toTarget.normalize(new Vector3d());
            Vector3d footDirection = foot.sub(hip, new Vector3d()).normalize();
            Vector3d side = thighVector.sub(footDirection.mul(thighVector.dot(footDirection), new Vector3d()), new Vector3d());

            if (side.lengthSquared() < REACH_MARGIN * REACH_MARGIN) {
                side = turnedMatrices[shin.getId()].transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).get(new Vector3d()).cross(shinVector);
            }

            side.sub(direction.mul(side.dot(direction), new Vector3d())).normalize();

            if (!side.isFinite() || !direction.isFinite()) {
                continue;
            }

            double cosine = Math.clamp((thighLength * thighLength + reach * reach - shinLength * shinLength) / (2.0D * thighLength * reach), -1.0D, 1.0D);
            Vector3d newThigh = direction.mul(thighLength * cosine, new Vector3d()).add(side.mul(thighLength * Math.sqrt(1.0D - cosine * cosine), new Vector3d()));
            Vector3d newShin = direction.mul(reach, new Vector3d()).sub(newThigh);
            Quaterniond thighTurn = new Quaterniond().rotationTo(thighVector.normalize(new Vector3d()), newThigh.normalize(new Vector3d()));
            Vector3d turnedShin = thighTurn.transform(new Vector3d(shinVector));
            Quaterniond shinTurn = new Quaterniond().rotationTo(turnedShin.normalize(), newShin.normalize());

            PoseOperators.rotateInModelSpace(armature, result, leg.thigh(), thighTurn);
            PoseOperators.rotateInModelSpace(armature, result, leg.shin(), shinTurn);
        }

        return result;
    }

    /// Ends the running step with the body and feet on the yaw.
    private void settle() {
        stepElapsed = -1.0F;
        originLag = 0.0F;
        bodyLag = 0.0F;
        Arrays.fill(plantedLags, 0.0F);
        Arrays.fill(goalLags, 0.0F);
    }

    /// How far `leg`'s foot has moved through its part of a step at `progress`, in [0, 1].
    private float legProgress(final ArmatureLoader.Leg leg, final float progress) {
        return (leg.leads() == ArmatureLoader.TurnDirection.RIGHT) == turnsRight
            ? Math.clamp((progress - LEAD_START) / (LEAD_END - LEAD_START), 0.0F, 1.0F)
            : Math.clamp((progress - FOLLOW_START) / (FOLLOW_END - FOLLOW_START), 0.0F, 1.0F);
    }

    /// A model space turn that shows the body `degrees` to the left of the yaw the renderer turns it by.
    private static Quaterniond yawRotation(final float degrees) {
        return new Quaterniond().rotateY(Math.toRadians(degrees));
    }
}
