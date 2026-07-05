package com.yesman.akythera.core.animation.keyframe;

import com.google.common.base.Predicate;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/// Snapshot of animation captured in a specific time
public final class Pose {
    /// A pose without any data inside it
    ///
    /// Difference between [Armature#tPose]: T pose has an affection when blending two poses
    /// while the empty pose will always be overwritten by another pose.
    public static final Pose EMPTY_POSE = new Pose().setUnmodifiable();

    private Map<String, Transform> boneTransforms = new HashMap<>();

    public void putBoneTransform(String name, Transform transform) {
        boneTransforms.put(name, transform);
    }

    public void removeBone(Predicate<? super Map.Entry<String, Transform>> predicate) {
        boneTransforms.entrySet().removeIf(predicate);
    }

    public void removeAllBones() {
        boneTransforms.clear();
    }

    public boolean hasTransform(String boneName) {
        return boneTransforms.containsKey(boneName);
    }

    public @Nullable Transform get(String boneName) {
        return boneTransforms.get(boneName);
    }

    public Transform orElseIdentity(String boneName) {
        return boneTransforms.getOrDefault(boneName, Transform.identity());
    }

    public Transform orElseZero(String boneName) {
        return boneTransforms.getOrDefault(boneName, Transform.zero());
    }

    public Transform orElse(String boneName, @NonNull Transform orElse) {
        return boneTransforms.getOrDefault(boneName, orElse);
    }

    public void forEachTransforms(BiConsumer<String, Transform> task) {
        boneTransforms.forEach(task);
    }

    /// Bound the pose in forward direction from a root bone
    ///
    /// Multiplies the pose transforms hierarchically, to get the final transform in the mesh space,
    /// commonly used in attaching items to render or calculate a collider transform of weapons in hand.
    ///
    /// Remind that the array should be bound in backward before rendering a skeletal mesh with
    /// [Armature#boundBackwardTransform]
    public Matrix4f[] getForwardBoundTransforms(AssetAccessor<? extends Armature> armature) {
        Matrix4f[] transforms = new Matrix4f[armature.get().getBoneCount()];
        boundTransformRecursively(armature.get().getRootBone(), new Matrix4f(), transforms);

        return transforms;
    }

    /// Multiply transforms recursively
    public void boundTransformRecursively(Bone bone, Matrix4f parentTransform, Matrix4f[] dest) {
        Matrix4f boundMatrix = new Matrix4f().mul(parentTransform).mul(bone.getLocalTransform()).mul(orElseIdentity(bone.getName()).toMatrix());
        dest[bone.getId()] = boundMatrix;

        for (Bone childBones : bone.getChildBones()) {
            boundTransformRecursively(childBones, boundMatrix, dest);
        }
    }

    /// Set the pose from another pose, if the key exists replace from `other`
    public void setPose(Pose other) {
        other.forEachTransforms(this::putBoneTransform);
    }

    /// Freeze pose transform entries
    public Pose setUnmodifiable() {
        boneTransforms = Collections.unmodifiableMap(boneTransforms);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pose Transforms(name)\n");

        for (Map.Entry<String, Transform> entry : boneTransforms.entrySet()) {
            Transform transform = entry.getValue();
            sb.append(
                String.format(
                    "%s : {%s, %s, %s}\n",
                    entry.getKey(),
                    transform.loc().toString(),
                    transform.rot().toString(),
                    transform.sca().toString()
                )
            );
        }

        return sb.toString();
    }

    /// Blend two poses with weights for each pose
    public static Pose blendPoses(Pose pose1, float weight1, Pose pose2, float weight2) {
        Pose pose = new Pose();
        float weightSum = weight1 + weight2;

        // optimized when weights are completely biased toward a single pose
        if (Float.compare(weightSum, weight1) == 0) {
            return pose1;
        } else if (Float.compare(weightSum, weight2) == 0) {
            return pose2;
        }

        float delta = weightSum == 0.0F ? 0.0F : weight2 / weightSum;

        Set<String> mergedPoseBones = new HashSet<>(pose1.boneTransforms.keySet());
        mergedPoseBones.addAll(pose2.boneTransforms.keySet());

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
    public static Pose blendPoses(Pair<Pose, Float>... posesToBlend) {
        Pose pose = new Pose();
        float weightSum = 0.0F;
        for (var pair : posesToBlend) weightSum += pair.getSecond();

        for (var pair : posesToBlend) {
            float weight = pair.getSecond() / weightSum;

            if (Float.compare(weight, 0.0F) == 1) {
                pair.getFirst().forEachTransforms((boneName, transform) -> {
                    pose.putBoneTransform(boneName, pose.orElseZero(boneName).add(transform.applyWeight(weight)));
                });
            }
        }

        pose.forEachTransforms((_, transform) -> {
            transform.rot().normalize();
        });

        return pose;
    }
}