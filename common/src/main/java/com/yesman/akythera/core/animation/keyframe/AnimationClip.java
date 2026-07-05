package com.yesman.akythera.core.animation.keyframe;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.core.math.algorithm.BinarySearchList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// An animation asset that holds
public final class AnimationClip {
    /// Bone keyframe sheet by names
    private final Map<String, BoneAnimationTrack> boneTransforms;

    /// Baked timestamps to ease the searching
    private final BinarySearchList<Float, Float> timeStamps;

    /// A max frame time
    private final float totalTime;

    private AnimationClip(Builder builder) {
        TreeSet<Float> allTimeStamps = new TreeSet<> ();

        // Gather all timestamps of bone animation to bake keyframe sheets
        builder.boneAnimations.values().forEach(boneAnimationTrack ->
            boneAnimationTrack.forEach((_, keyframe) -> allTimeStamps.add(keyframe.timeStamp()))
        );

        ImmutableMap.Builder<@NonNull String, @NonNull BoneAnimationTrack> bakedJointTransforms = ImmutableMap.builder();

        builder.boneAnimations.forEach((jointName, boneAnimationTrack) -> {
            // Bake keyframe sheet by all timestamps
            bakedJointTransforms.put(jointName, BoneAnimationTrack.bakeKeyframes(boneAnimationTrack, allTimeStamps));
        });

        boneTransforms = bakedJointTransforms.build();
        BinarySearchList.Builder<Float, Float> timstampsBuilder = BinarySearchList.builder(Function.<Float>identity(), true);

        for (float f : allTimeStamps) {
            timstampsBuilder.add(f);
        }

        timeStamps = timstampsBuilder.create();
        totalTime = allTimeStamps.isEmpty() ? 0.0F : allTimeStamps.getLast();
    }

    /// Returns whether the clip has a bone transform
    public boolean hasBoneTransform(String boneName) {
        return boneTransforms.containsKey(boneName);
    }

    /// Returns a bone transform
    public @Nullable BoneAnimationTrack getBoneTransform(String boneName) {
        return boneTransforms.get(boneName);
    }

    /// Visits all tracks of the animation clip
    public void visitBoneTracks(BiConsumer<String, BoneAnimationTrack> task) {
        for (Map.Entry<String, BoneAnimationTrack> entry : boneTransforms.entrySet()) {
            task.accept(entry.getKey(), entry.getValue());
        }
    }

    /// Returns pose in a time
    public Pose getPose(float time) {
        Pose pose = new Pose();

        if (time < 0.0F) {
            time = totalTime + time;
        }

        Pair<Integer, Integer> fronAndBack = timeStamps.getFrontAndBackIndices(time);

        for (String jointName : boneTransforms.keySet()) {
            pose.putBoneTransform(jointName, boneTransforms.get(jointName).getTransformAt(fronAndBack.getFirst(), fronAndBack.getSecond(), time));
        }

        return pose;
    }

    /// Returns a total frame time
    public float getTotalTime() {
        return totalTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, BoneAnimationTrack> boneAnimations = new HashMap<> ();

        public void addBoneAnimation(String boneName, BoneAnimationTrack boneAnimationTrack) {
            boneAnimations.put(boneName, boneAnimationTrack);
        }

        public BoneAnimationTrack getBoneTrack(String boneName) {
            return boneAnimations.get(boneName);
        }

        public AnimationClip build() {
            return new AnimationClip(this);
        }
    }
}