package com.yesman.akythera.core.animation.keyframe;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/// Snapshot of animation captured in a specific time
@ScriptType
public final class Pose {
    /// A pose without any data inside it
    ///
    /// Difference between [Armature#getTPose]: T pose has an affection when blending
    /// two poses while the empty pose will always be overwritten by another pose.
    public static final Pose EMPTY_POSE = new Pose().setUnmodifiable();

    private Map<String, Transform> boneTransforms = new HashMap<>();

    // Empty pose constructor
    public Pose() {}

    // Copy constructor
    public Pose(final Pose pose) {
        pose.forEachTransforms(this::putBoneTransform);
    }

    public void putBoneTransform(final String name, Transform transform) {
        boneTransforms.put(name, transform);
    }

    public boolean hasTransform(final String boneName) {
        return boneTransforms.containsKey(boneName);
    }

    public @Nullable Transform get(final String boneName) {
        return boneTransforms.get(boneName);
    }

    public Transform orElseIdentity(final String boneName) {
        return boneTransforms.getOrDefault(boneName, Transform.identity());
    }

    public Transform orElseZero(final String boneName) {
        return boneTransforms.getOrDefault(boneName, Transform.zero());
    }

    public Transform orElse(final String boneName, @NonNull Transform orElse) {
        return boneTransforms.getOrDefault(boneName, orElse);
    }

    public void forEachTransforms(final BiConsumer<String, Transform> task) {
        boneTransforms.forEach(task);
    }

    public Set<String> boneNameSet() {
        return boneTransforms.keySet();
    }

    /// Bound the pose in forward direction from a root bone
    ///
    /// Multiplies the pose transforms hierarchically, to get the final transform in the mesh space,
    /// commonly used in attaching items to render or calculate a collider transform of weapons in hand.
    ///
    /// Remind that the array should be bound in backward before rendering a skeletal mesh with
    /// [Armature#boundBackwardTransform]
    public Matrix4f[] getForwardBoundTransforms(final AssetAccessor<? extends Armature> armature) {
        return getForwardBoundTransforms(
            armature, Matrix4fUtils.allocateMatrixArray(armature.get().getBoneCount()));
    }

    /// Into `dest`, a caller-owned array of non-null matrices
    /// ([Matrix4fUtils#allocateMatrixArray]) rewritten in place and returned.
    public Matrix4f[] getForwardBoundTransforms(final AssetAccessor<? extends Armature> armature, final Matrix4f[] dest) {
        boundTransformRecursively(armature.get().getRootBone(), new Matrix4f(), dest);

        return dest;
    }

    /// Multiply transforms recursively
    public void boundTransformRecursively(final Bone bone, final Matrix4f parentTransform, final Matrix4f[] dest) {
        Matrix4f boundMatrix = dest[bone.getId()];
        boundMatrix.set(parentTransform)
            .mul(bone.getLocalTransform())
            .mul(orElseIdentity(bone.getName()).toMatrix());

        for (Bone childBones : bone.getChildBones()) {
            boundTransformRecursively(childBones, boundMatrix, dest);
        }
    }

    /// Set the pose from another pose, if the key exists replace from `other`
    public void setPose(final Pose other) {
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
}