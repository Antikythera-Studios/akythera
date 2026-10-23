package com.yesman.akythera.core.util.helper;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.math.Transform;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class PoseOperators {
    private PoseOperators() {}

    public static Pose blendPoses(final Pose pose1, float weight1, final Pose pose2, float weight2) {
        Pose pose = new Pose();
        weight1 = Math.max(weight1, 0.0F);
        weight2 = Math.max(weight2, 0.0F);
        float weightSum = weight1 + weight2;

        // optimized when weights are completely biased toward a single pose
        if (Float.compare(weightSum, weight1) == 0) {
            return pose1;
        } else if (Float.compare(weightSum, weight2) == 0) {
            return pose2;
        }

        float delta = weightSum == 0.0F ? 0.0F : weight2 / weightSum;

        Set<String> mergedPoseBones = new HashSet<>(pose1.boneNameSet());
        mergedPoseBones.addAll(pose2.boneNameSet());

        for (String boneName : mergedPoseBones) {
            if (!pose1.hasTransform(boneName)) {
                pose.putBoneTransform(boneName, pose2.orElseIdentity(boneName));
            } else if (!pose2.hasTransform(boneName)) {
                pose.putBoneTransform(boneName, pose1.orElseIdentity(boneName));
            } else {
                pose.putBoneTransform(
                    boneName,
                    Transform.lerp(
                        pose1.orElseIdentity(boneName),
                        pose2.orElseIdentity(boneName),
                        delta
                    )
                );
            }
        }

        return pose;
    }

    /// Blend multiple poses with weights for each pose. Consider using [#blendPoses(Pose, float, Pose, float)] for
    /// better performance if you only bind two animations.
    ///
    /// @param posesToBlend each pose's bone entry should be matching, otherwise unmatching bones will be mixed with T-Pose
    @SafeVarargs
    public static Pose blendPoses(final Pair<Pose, Float>... posesToBlend) {
        Pose pose = new Pose();
        float weightSum = 0.0F;

        for (var pair : posesToBlend) weightSum += pair.getSecond();

        for (var pair : posesToBlend) {
            float weight = pair.getSecond() / weightSum;

            if (Float.compare(weight, 0.0F) == 1) {
                pair.getFirst().forEachTransforms((boneName, transform) -> {
                    Transform sum = pose.orElseZero(boneName);
                    Transform weighted = transform.applyWeight(weight);
                    Quaterniond rot = weighted.rot();

                    // q and -q are one rotation, summed across hemispheres they cancel out
                    if (sum.rot().dot(rot) < 0.0) rot.set(-rot.x(), -rot.y(), -rot.z(), -rot.w());

                    pose.putBoneTransform(boneName, sum.add(weighted));
                });
            }
        }

        pose.forEachTransforms((_, transform) -> transform.rot().normalize());

        return pose;
    }

    /// An animation that provides `additivePose` should be imported as [AnimationLoader.ImportType#ADDITIVE]
    public static Pose additivePose(final Pose basePose, final Pose additivePose, final float weight) {
        Pose pose = new Pose(basePose);

        additivePose.forEachTransforms((name, transform) ->
            pose.putBoneTransform(name, pose.orElseIdentity(name).applyAdditive(transform, weight))
        );

        return pose;
    }

    /// Mixes `layered` onto `base` bone by bone.
    ///
    /// @param boneWeights        the share of `layered` per bone id of `armature`
    /// @param weight             scales every bone's share
    /// @param meshSpaceRotation  whether rotations mix in model space instead of each bone's own
    public static Pose layeredBlendPerBone(
        final Pose base, final Pose layered, final Armature armature, final float[] boneWeights, final float weight,
        final boolean meshSpaceRotation
    ) {
        Pose pose = new Pose();
        layeredBlendRecursively(
            armature.getRootBone(), base, layered, pose, boneWeights, weight, meshSpaceRotation,
            new Quaterniond(), new Quaterniond(), new Quaterniond()
        );

        return pose;
    }

    /// @param baseParent    the parent's model rotation in `base`, read only when blending in model space
    /// @param layeredParent the parent's model rotation in `layered`
    /// @param destParent    the parent's model rotation in `dest`
    private static void layeredBlendRecursively(
        final Bone bone, final Pose base, final Pose layered, final Pose dest, final float[] boneWeights,
        final float weight, final boolean meshSpaceRotation,
        final Quaterniond baseParent, final Quaterniond layeredParent, final Quaterniond destParent
    ) {
        String name = bone.getName();
        float share = boneWeights[bone.getId()] * weight;
        Transform baseTransform = base.orElseIdentity(name);
        Transform layeredTransform = layered.orElseIdentity(name);
        Transform mixed = share <= 0.0F ? baseTransform : Transform.lerp(baseTransform, layeredTransform, share);

        Quaterniond baseModel = baseParent;
        Quaterniond layeredModel = layeredParent;
        Quaterniond destModel = destParent;

        if (meshSpaceRotation) {
            Quaterniond rest = bone.getLocalTransform().getNormalizedRotation(new Quaterniond());
            baseModel = new Quaterniond(baseParent).mul(rest).mul(baseTransform.rot());
            layeredModel = new Quaterniond(layeredParent).mul(rest).mul(layeredTransform.rot());

            if (share > 0.0F) {
                Quaterniond frame = new Quaterniond(destParent).mul(rest);
                Quaterniond desired = new Quaterniond(baseModel).slerp(layeredModel, share);
                mixed = new Transform(mixed.loc(), frame.conjugate().mul(desired).normalize(), mixed.sca());
            }

            destModel = new Quaterniond(destParent).mul(rest).mul(mixed.rot());
        }

        dest.putBoneTransform(name, mixed);

        for (Bone childBone : bone.getChildBones()) {
            layeredBlendRecursively(
                childBone, base, layered, dest, boneWeights, weight, meshSpaceRotation, baseModel, layeredModel, destModel);
        }
    }

    public static @Nullable Quaterniond boneFrameRotation(final Armature armature, final Pose pose, final String boneName) {
        return boneFrameRotation(armature.getRootBone(), pose, boneName, new Quaterniond());
    }

    private static @Nullable Quaterniond boneFrameRotation(
        final Bone bone, final Pose pose, final String boneName, final Quaterniond parentRotation
    ) {
        Quaterniond frame = parentRotation.mul(bone.getLocalTransform().getNormalizedRotation(new Quaterniond()), new Quaterniond());

        if (bone.getName().equals(boneName)) {
            return frame;
        }

        frame.mul(pose.orElseIdentity(bone.getName()).rot());

        for (Bone childBone : bone.getChildBones()) {
            Quaterniond found = boneFrameRotation(childBone, pose, boneName, frame);

            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
