package com.yesman.akythera.core.animation.driver;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/// Turns one bone by a pitch, yaw and roll in degrees (UE's "Transform (Modify) Bone").
///
/// [#replaceUpdater] 0 adds the turn on top of the animated bone. 1 turns the bone's rest orientation instead,
/// dropping its animated rotation; in [Space#MODEL] that drops its parents' motion too, so a head keeps its aim
/// while the body sways. Values between mix the two.
public final class ModifyBoneDriver extends AbstractWrapperDriver {
    private final Animator animator;
    private final String boneName;
    private final Space space;

    /// Pitch, yaw and roll in degrees, applied yaw first. Signed as an entity's own rotation.
    private final Supplier<Vec3> rotationUpdater;

    /// How much of the bone's animated rotation the turn replaces, from 0.0 to 1.0.
    private final Supplier<Float> replaceUpdater;

    public ModifyBoneDriver(
        final Animator animator, final AnimationDriver base, final String boneName, final Space space,
        final Supplier<Vec3> rotationUpdater, final Supplier<Float> replaceUpdater
    ) {
        super(base);

        this.animator = animator;
        this.boneName = boneName;
        this.space = space;
        this.rotationUpdater = rotationUpdater;
        this.replaceUpdater = replaceUpdater;
    }

    /// Turns the hips by `angle` degrees of yaw and the spine back by as much, so the legs face the movement while
    /// the upper body keeps facing forward (UE's "Orientation Warping").
    public static ModifyBoneDriver orientationWarp(
        final Animator animator, final AnimationDriver base, final String hipBoneName, final String spineBoneName,
        final Supplier<Float> angle
    ) {
        ModifyBoneDriver hip = new ModifyBoneDriver(
            animator, base, hipBoneName, Space.MODEL, () -> new Vec3(0.0D, angle.get(), 0.0D), () -> 0.0F);

        return new ModifyBoneDriver(
            animator, hip, spineBoneName, Space.MODEL, () -> new Vec3(0.0D, -angle.get(), 0.0D), () -> 0.0F);
    }

    @Override
    public Pose getPose() {
        Pose pose = base.getPose();
        Armature armature = animator.getArmature().get();
        Bone bone = armature.getBoneByName(boneName);

        if (bone == null) {
            return pose;
        }

        Vec3 degrees = rotationUpdater.get();
        Quaterniond rotation = new Quaterniond()
            .rotateY(Math.toRadians(-degrees.y))
            .rotateX(Math.toRadians(-degrees.x))
            .rotateZ(Math.toRadians(-degrees.z));
        float keep = 1.0F - Math.clamp(replaceUpdater.get(), 0.0F, 1.0F);
        Transform transform = pose.orElseIdentity(boneName);

        Quaterniond modified = switch (space) {
            case LOCAL -> new Quaterniond().slerp(transform.rot(), keep).mul(rotation);
            case PARENT -> {
                Quaterniond rest = bone.getLocalTransform().getNormalizedRotation(new Quaterniond());
                yield new Quaterniond(rest).conjugate().mul(rotation).mul(rest).mul(new Quaterniond().slerp(transform.rot(), keep));
            }
            case MODEL -> {
                Quaterniond frame = PoseOperators.boneFrameRotation(armature, pose, boneName);
                Quaterniond neutral = PoseOperators.boneFrameRotation(armature, armature.getTPose(), boneName);
                Quaterniond motion = new Quaterniond(neutral).conjugate().mul(frame).mul(transform.rot());
                yield new Quaterniond(frame).conjugate().mul(rotation).mul(neutral).mul(new Quaterniond().slerp(motion, keep));
            }
        };

        Pose result = new Pose(pose);
        result.putBoneTransform(boneName, new Transform(transform.loc(), modified.normalize(), transform.sca()));

        return result;
    }

    public enum Space implements StringRepresentable {
        LOCAL("local"), // the bone's own axes
        PARENT("parent"), // the parent bone's axes
        MODEL("model"); // the model's axes

        public static final Codec<Space> CODEC = StringRepresentable.fromEnum(Space::values);

        private final String name;

        Space(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
