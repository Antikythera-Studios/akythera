package com.yesman.akythera.core.asset.armature;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.math.Transform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/// A core component for the skeletal mesh system, not only for
/// mesh deformation in client side but also for calculating bone
/// transform in a specific animation time, to apply the transform
/// to colliders.
///
/// Allowed inheriting this class for convenient access to bones.
public class Armature {
    /// Bones by [Bone#getId()]
    private final Int2ObjectMap<Bone> boneById;

    /// Bones by [Bone#getName()]
    private final Map<String, Bone> boneByName;

    /// Number of bones
    private final int boneCount;

    /// A bone located in the top of the bone hierarchy
    private final Bone rootBone;

    /// A pose with identity transform matrices, DO NOT modify the pose.
    private final Pose tPose;

    public Armature(final Bone rootBone, final int boneCount, final Map<String, Bone> bonesByName) {
        this.boneCount = boneCount;
        this.rootBone = rootBone;
        this.boneByName = bonesByName;

        boneById = new Int2ObjectOpenHashMap<>();
        tPose = new Pose();

        this.boneByName.values().forEach(bone -> {
            boneById.put(bone.getId(), bone);
            tPose.putBoneTransform(bone.getName(), Transform.identity());
        });

        tPose.setUnmodifiable();
    }

    /// Check whether named bone exists
    public boolean hasBone(final String name) {
        return boneByName.containsKey(name);
    }

    /// Returns a bone by ID. the ID are specified by vertex group order in Blender.
    public @Nullable Bone getBoneById(final int id) {
        return boneById.get(id);
    }

    /// Returns a bone by name
    public @Nullable Bone getBoneByName(final String name) {
        return boneByName.get(name);
    }

    /// Initializing purpose
    public @Nullable Bone getBoneByName(final String name, final Consumer<NoSuchElementException> exceptionHandler) {
        if (!boneByName.containsKey(name)) {
            exceptionHandler.accept(new NoSuchElementException("No bone named " + name));
            return null;
        }

        return boneByName.get(name);
    }

    /// Initializing purpose
    public @NonNull Bone getBoneByNameOrThrow(final String name) {
        if (!boneByName.containsKey(name)) {
            throw new NoSuchElementException("No bone named " + name);
        }

        return boneByName.get(name);
    }

    /// Iterate bones (tree order is not ensured)
    public void visitBones(final Consumer<Bone> bone) {
        boneByName.values().forEach(bone);
    }

    /// Returns a bone in the top of the hierarchy
    public Bone getRootBone() {
        return rootBone;
    }

    /// Returns a number of bones
    public int getBoneCount() {
        return boneCount;
    }

    /// Returns a pose with no transform, conventionally called the T-Pose
    public Pose getTPose() {
        return tPose;
    }

    /// Apply the backward transform, by multiplying [Bone#getInverseTransformToOrigin()] to get a **fully** bound animation transforms.
    ///
    /// Inverse transform will be applied first for calculating bone transform to apply rotation in the origin point,
    /// the final pose is determined by hierarchical visit of child bones
    ///
    /// The armature should be the same one that is used as [Pose#getForwardBoundTransforms]'s parameter.
    ///
    /// @param forwardBoundTransforms A matrix array returned by [Pose#getForwardBoundTransforms]
    public void boundBackwardTransform(Matrix4f[] forwardBoundTransforms) {
        for (int i = 0; i < boneCount; i++) {
            Bone bone = getBoneById(i);
            if (bone == null) continue;
            forwardBoundTransforms[i].mul(bone.getInverseTransformToOrigin(), forwardBoundTransforms[i]);
        }
    }
}
