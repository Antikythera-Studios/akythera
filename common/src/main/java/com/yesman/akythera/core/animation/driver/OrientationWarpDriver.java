package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.joml.Quaterniond;

import java.util.function.Supplier;

public final class OrientationWarpDriver extends AbstractWrapperDriver {
    private final Animator animator;
    private final String hipBoneName;
    private final String spineBoneName;
    private final Supplier<Float> angleUpdater;

    public OrientationWarpDriver(
        final Animator animator, final AnimationDriver base, final String hipBoneName,
        final String spineBoneName, final Supplier<Float> angleUpdater
    ) {
        super(base);

        this.animator = animator;
        this.hipBoneName = hipBoneName;
        this.spineBoneName = spineBoneName;
        this.angleUpdater = angleUpdater;
    }

    @Override
    public Pose getPose() {
        Pose pose = base.getPose();
        float angle = angleUpdater.get();

        if (angle == 0.0F) {
            return pose;
        }

        Armature armature = animator.getArmature().get();
        Quaterniond hipFrame = PoseOperators.boneFrameRotation(armature, pose, hipBoneName);
        Quaterniond spineFrame = PoseOperators.boneFrameRotation(armature, pose, spineBoneName);

        if (hipFrame == null || spineFrame == null) {
            return pose;
        }

        Transform hip = pose.orElseIdentity(hipBoneName);
        Quaterniond turn = new Quaterniond().rotateY(Math.toRadians(-angle));
        Pose result = new Pose(pose);
        result.putBoneTransform(hipBoneName, new Transform(
            hip.loc(), new Quaterniond(hipFrame).conjugate().mul(turn).mul(hipFrame).mul(hip.rot()).normalize(), hip.sca()));

        Transform spine = pose.orElseIdentity(spineBoneName);
        Quaterniond warpedSpineFrame = PoseOperators.boneFrameRotation(armature, result, spineBoneName);
        result.putBoneTransform(spineBoneName, new Transform(
            spine.loc(), warpedSpineFrame.conjugate().mul(spineFrame).mul(spine.rot()).normalize(), spine.sca()));

        return result;
    }
}
