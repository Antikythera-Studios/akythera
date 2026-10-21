package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.function.Supplier;

public final class LookAtDriver extends AbstractWrapperDriver {
    private final Animator animator;
    private final String boneName;
    private final Supplier<Vec3> rotationUpdater;
    private final Supplier<Float> animationShareUpdater;

    public LookAtDriver(
        final Animator animator, final AnimationDriver base, final String boneName,
        final Supplier<Vec3> rotationUpdater, final Supplier<Float> animationShareUpdater
    ) {
        super(base);

        this.animator = animator;
        this.boneName = boneName;
        this.rotationUpdater = rotationUpdater;
        this.animationShareUpdater = animationShareUpdater;
    }

    @Override
    public Pose getPose() {
        Pose pose = base.getPose();
        Armature armature = animator.getArmature().get();
        Quaterniond frame = PoseOperators.boneFrameRotation(armature, pose, boneName);

        if (frame == null) {
            return pose;
        }

        Vec3 rotation = rotationUpdater.get();
        Transform bone = pose.orElseIdentity(boneName);
        Quaterniond neutral = PoseOperators.boneFrameRotation(armature, armature.getTPose(), boneName);
        Quaterniond animationMotion = new Quaterniond(neutral).conjugate().mul(frame).mul(bone.rot()).normalize();
        Quaterniond target = new Quaterniond()
            .rotateY(Math.toRadians(-rotation.y))
            .rotateX(Math.toRadians(-rotation.x))
            .rotateZ(Math.toRadians(-rotation.z))
            .mul(neutral)
            .mul(new Quaterniond().slerp(animationMotion, animationShareUpdater.get()));

        Pose result = new Pose(pose);
        result.putBoneTransform(boneName, new Transform(bone.loc(), frame.conjugate().mul(target).normalize(), bone.sca()));

        return result;
    }
}
