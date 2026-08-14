package com.yesman.akythera.core.asset.armature;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A member of [Armature]
public final class Bone {
    /// Bone ID in [Armature]
    private final int boneId;

    /// Bone Name in [Armature]
    private final String boneName;

    /// Local transform relative to a parent bone
    private final Matrix4f localTransform;

    /// Transform matrix to origin(0, 0, 0). Used in calculating local animation transform
    private final Matrix4f inverseTransformToOrigin = new Matrix4f();

    private final List<Bone> childBones = new ArrayList<> ();

    public Bone(final String name, final int boneId, final Matrix4f localTransform) {
        this.boneId = boneId;
        this.boneName = name;
        this.localTransform = localTransform;
    }

    public void addChildBones(final Bone... bones) {
        for (Bone bone : bones) {
            if (!childBones.contains(bone)) {
                childBones.add(bone);
            }
        }
    }

    public List<Bone> getAllBones() {
        List<Bone> list = new ArrayList<>();
        gatherChildBones(list);

        return list;
    }

    public void iterateChildBones(final Consumer<Bone> iterTask) {
        iterTask.accept(this);

        for (Bone bone : childBones) {
            bone.iterateChildBones(iterTask);
        }
    }

    private void gatherChildBones(final List<Bone> list) {
        list.add(this);

        for (Bone bone : childBones) {
            bone.gatherChildBones(list);
        }
    }

    /// Initialize inverse transform to the origin point(0, 0, 0).
    ///
    /// Memorized the inverse transform to accelerate animation calculation
    @ApiStatus.Internal
    public void initOriginTransform(final Matrix4f parentTransform) {
        Matrix4f modelTransform = parentTransform.mul(localTransform, new Matrix4f());
        modelTransform.invert(inverseTransformToOrigin);

        for (Bone bone : childBones) {
            bone.initOriginTransform(modelTransform);
        }
    }

    public Matrix4f getLocalTransform() {
        return localTransform;
    }

    public Matrix4f getInverseTransformToOrigin() {
        return inverseTransformToOrigin;
    }

    public List<Bone> getChildBones() {
        return childBones;
    }

    // Null if index out of range
    @Nullable
    public Bone getChildBone(final int index) {
        if (index < 0 || childBones.size() <= index) {
            return null;
        }

        return childBones.get(index);
    }

    public String getName() {
        return boneName;
    }

    public int getId() {
        return boneId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof Bone bone) {
            return boneName.equals(bone.boneName) && boneId == bone.boneId;
        } else {
            return super.equals(o);
        }
    }

    @Override
    public int hashCode() {
        // apply bit operate to bone name hash and bone id
        return boneName.hashCode() ^ boneId;
    }
}
