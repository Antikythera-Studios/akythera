package com.yesman.akythera.core.animation.driver;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/// Turns the whole pose about a pivot bone so the body pitches with the owner's view or along its motion and banks into
/// its turns, easing toward both: a swimmer, a diver, a flyer.
///
/// The bank is the lean a turn at the owner's speed and yaw rate takes under its own gravity, `atan(v·ω/g)`.
public final class OrientBodyDriver extends AbstractWrapperDriver {
    /// Time constant of the easing toward the target pitch and bank, in seconds.
    private static final float EASE_TIME = 0.2F;

    /// Time constant of the easing toward a [PitchSource#MOTION] pitch, in seconds.
    private static final float MOTION_EASE_TIME = 0.5F;

    /// Slowest motion, in blocks per tick, that still sets a [PitchSource#MOTION] pitch.
    private static final double MIN_PITCH_SPEED = 0.02D;

    private final Animator animator;
    private final String pivotBoneName;
    private final PitchSource pitchSource;

    /// Most pitch and roll in degrees, as pitch, yaw and roll. Yaw is unused.
    private final Supplier<Vec3> limitsUpdater;

    /// How much of the turn applies, from 0.0 to 1.0.
    private final Supplier<Float> weightUpdater;

    /// Eased pitch and roll in degrees, signed as [ModifyBoneDriver]'s: pitch down and roll toward the right side.
    private float pitch;
    private float roll;

    /// Model-space bone matrices, reused each frame to find the pivot.
    private Matrix4f[] boneTransforms;

    public OrientBodyDriver(
        final Animator animator, final AnimationDriver base, final String pivotBoneName, final PitchSource pitchSource,
        final Supplier<Vec3> limitsUpdater, final Supplier<Float> weightUpdater
    ) {
        super(base);

        this.animator = animator;
        this.pivotBoneName = pivotBoneName;
        this.pitchSource = pitchSource;
        this.limitsUpdater = limitsUpdater;
        this.weightUpdater = weightUpdater;
    }

    @Override
    public void start() {
        super.start();

        pitch = 0.0F;
        roll = 0.0F;
    }

    @Override
    public void update(final float frameTime) {
        super.update(frameTime);

        float weight = Math.clamp(weightUpdater.get(), 0.0F, 1.0F);
        float targetPitch = 0.0F;
        float targetRoll = 0.0F;

        if (weight > 0.0F && animator.getOwner() instanceof EntityPatch<?> patch) {
            Entity entity = patch.getOriginal();
            Vec3 limits = limitsUpdater.get();
            double dx = entity.getX() - entity.xo;
            double dy = entity.getY() - entity.yo;
            double dz = entity.getZ() - entity.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yawStep = entity instanceof LivingEntity living
                ? Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO)
                : Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
            double gravity = entity.getGravity();

            if (gravity > 0.0D) {
                targetRoll = (float)Math.toDegrees(Math.atan(horizontal * Math.toRadians(yawStep) / gravity));
            }

            targetPitch = switch (pitchSource) {
                case VIEW -> entity.getXRot();
                case MOTION -> horizontal * horizontal + dy * dy > MIN_PITCH_SPEED * MIN_PITCH_SPEED
                    ? (float)Math.toDegrees(Math.atan2(-dy, horizontal))
                    : pitch;
            };
            targetPitch = Mth.clamp(targetPitch, (float)-limits.x, (float)limits.x) * weight;
            targetRoll = Mth.clamp(targetRoll, (float)-limits.z, (float)limits.z) * weight;
        }

        float blend = 1.0F - (float)Math.exp(-frameTime / EASE_TIME);
        float pitchBlend = pitchSource == PitchSource.MOTION ? 1.0F - (float)Math.exp(-frameTime / MOTION_EASE_TIME) : blend;
        pitch += (targetPitch - pitch) * pitchBlend;
        roll += (targetRoll - roll) * blend;
    }

    @Override
    public Pose getPose() {
        Pose pose = base.getPose();
        AssetAccessor<? extends Armature> armatureAccessor = animator.getArmature();
        Armature armature = armatureAccessor.get();
        Bone pivot = armature.getBoneByName(pivotBoneName);

        if (pivot == null || Math.abs(pitch) < 0.01F && Math.abs(roll) < 0.01F) {
            return pose;
        }

        if (boneTransforms == null || boneTransforms.length != armature.getBoneCount()) {
            boneTransforms = Matrix4fUtils.allocateMatrixArray(armature.getBoneCount());
        }

        Vector3f pivotPosition = pose.getForwardBoundTransforms(armatureAccessor, boneTransforms)[pivot.getId()].getTranslation(new Vector3f());
        Quaterniond turn = new Quaterniond().rotateX(Math.toRadians(-pitch)).rotateZ(Math.toRadians(-roll));
        Bone root = armature.getRootBone();
        Matrix4d rest = new Matrix4d(root.getLocalTransform());
        Matrix4d aboutPivot = new Matrix4d(rest).invert()
            .translate(pivotPosition.x, pivotPosition.y, pivotPosition.z)
            .rotate(turn)
            .translate(-pivotPosition.x, -pivotPosition.y, -pivotPosition.z)
            .mul(rest);
        Transform transform = pose.orElseIdentity(root.getName());
        Vector3d location = aboutPivot.transformPosition(new Vector3d(transform.loc().x, transform.loc().y, transform.loc().z));
        Quaterniond rotation = aboutPivot.getNormalizedRotation(new Quaterniond()).mul(transform.rot()).normalize();

        Pose result = new Pose(pose);
        result.putBoneTransform(root.getName(), new Transform(new Vec3(location.x, location.y, location.z), rotation, transform.sca()));

        return result;
    }

    public enum PitchSource implements StringRepresentable {
        VIEW("view"), // the owner's look pitch
        MOTION("motion"); // the slope of the owner's last move

        public static final Codec<PitchSource> CODEC = StringRepresentable.fromEnum(PitchSource::values);

        private final String name;

        PitchSource(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
