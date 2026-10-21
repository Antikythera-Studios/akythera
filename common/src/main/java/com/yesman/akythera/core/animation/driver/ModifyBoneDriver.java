package com.yesman.akythera.core.animation.driver;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.PoseOperators;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

public final class ModifyBoneDriver extends AbstractWrapperDriver {
    private final Animator animator;
    private final String boneName;
    private final Space space;
    private final Supplier<Vec3> rotationUpdater;

    public ModifyBoneDriver(
        final Animator animator, final AnimationDriver base, final String boneName,
        final Space space, final Supplier<Vec3> rotationUpdater
    ) {
        super(base);

        this.animator = animator;
        this.boneName = boneName;
        this.space = space;
        this.rotationUpdater = rotationUpdater;
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
        Quaterniond rotation = QuaternionUtils.fromEulerDegreesXYZ(new Vector3d(degrees.x, degrees.y, degrees.z));
        Transform transform = pose.orElseIdentity(boneName);
        Quaterniond current = new Quaterniond(transform.rot());

        Quaterniond modified = switch (space) {
            case LOCAL -> current.mul(rotation);
            case PARENT -> {
                Quaterniond rest = bone.getLocalTransform().getNormalizedRotation(new Quaterniond());
                yield new Quaterniond(rest).conjugate().mul(rotation).mul(rest).mul(current);
            }
            case MODEL -> {
                Quaterniond frame = PoseOperators.boneFrameRotation(armature, pose, boneName);
                yield new Quaterniond(frame).conjugate().mul(rotation).mul(frame).mul(current);
            }
        };

        Pose result = new Pose(pose);
        result.putBoneTransform(boneName, new Transform(transform.loc(), modified.normalize(), transform.sca()));

        return result;
    }

    public enum Space implements StringRepresentable {
        LOCAL("local"),
        PARENT("parent"),
        MODEL("model");

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
