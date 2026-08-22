package com.yesman.akythera.core.util.helper;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.math.Transform;

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
    /// better performance if you only bind two animationsToReplace.
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
                pair.getFirst().forEachTransforms((boneName, transform) ->
                    pose.putBoneTransform(boneName, pose.orElseZero(boneName).add(transform.applyWeight(weight)))
                );
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

    /// @param boneName the bone the layered pose starts taking over from. The bone itself keeps the
    /// base pose; its descendants ramp toward `layered` over `depth` bones.
    /// @param depth    bones to ramp the blend over. 0 or less hands the whole branch below `boneName`
    /// to `layered` at once.
    public static Pose layeredBlendPerBone(
        final Pose base, final Pose layered, final String boneName, final int depth,
        final AssetAccessor<? extends Armature> armature
    ) {
        Bone rootBone = armature.get().getRootBone();
        Pose pose = new Pose();
        layeredBlendRecursively(rootBone, boneName, base, layered, pose, 0.0F, depth <= 0 ? 1.0F : 1.0F / depth,
            boneName.equals(rootBone.getName()));

        return pose;
    }

    private static void layeredBlendRecursively(
        final Bone bone, final String boneName, final Pose base, final Pose layered, final Pose dest,
        final float weight, final float increment, final boolean isLayer
    ) {
        boolean isLayerNext;

        if (isLayer) {
            dest.putBoneTransform(bone.getName(), Transform.lerp(base.orElseIdentity(bone.getName()), layered.orElseIdentity(bone.getName()), weight));
            isLayerNext = true;
        } else {
            dest.putBoneTransform(bone.getName(), base.orElseIdentity(bone.getName()));
            isLayerNext = boneName.equals(bone.getName());
        }

        float nextWeight = Math.min(1.0F, weight + (isLayerNext ? increment : 0.0F));

        for (Bone childBone : bone.getChildBones()) {
            layeredBlendRecursively(childBone, boneName, base, layered, dest, nextWeight, increment, isLayerNext);
        }
    }
}
