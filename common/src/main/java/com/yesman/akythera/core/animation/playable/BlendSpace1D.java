package com.yesman.akythera.core.animation.playable;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.TreeMap;

/// A mixture of multiple animation nodes placed in 1D array shape
///
/// /// Inspired by [UE Blend Space](https://dev.epicgames.com/documentation/unreal-engine/blend-spaces-in-unreal-engine)

@Deprecated // In-progress
public final class BlendSpace1D implements PlayableAnimation {
    // TODO: Finish Codec
    // public static final Codec<BlendSpace1D> CODEC;
    // private final BinarySearchList<AssetAccessor<AnimationClip>, Float> ;

    @Override
    public Pose getPose(float time) {
        return null;
    }

    @Override
    public float fadeInTime() {
        return 0;
    }

    @Override
    public float fadeOutTime() {
        return 0;
    }

    @Override
    public float getTotalFrameTime() {
        return 0;
    }

    @Override
    public boolean loops() {
        return false;
    }

    @Override
    public void tick(ComponentAttachable owner, float prevElapsedTime, float currentElapsedTime) {
    }

    /// Instantiated version of [BlendSpace2D]. To keep the design intention of [PlayableAnimation] and [AnimationDriver]
    public static final class BlendSpace1DDriver implements AnimationDriver {
        //TODO: Maybe need AssetAccessor?
        private BlendSpace1D referencing;
        private float delta;

        /// Updates the destination coordinate
        public void updateDelta(final float delta) {
            this.delta = delta;
        }

        @Override
        public Pose getPose() {
            return null;
        }

        @Override
        public @Nullable PlayableAnimation getPlayingAnimation() {
            return referencing;
        }

        @Override
        public void update(float frameTime) {

        }

        @Override
        public float getTime() {
            return 0;
        }

        @Override
        public void start() {

        }

        @Override
        public void terminate() {

        }

        @Override
        public float getWeight() {
            return 1.0F;
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean fadingInOrOut() {
            return false;
        }

        @Override
        public float getFadeTime() {
            return 0;
        }

        @Override
        public void syncDriverTo(@NonNull AnimationDriver source) {

        }
    }

    public static BlendSpace1DBuilder builder() {
        return new BlendSpace1DBuilder();
    }

    public static class BlendSpace1DBuilder {
        boolean loops = false;
        final TreeMap<Float, AssetAccessor<? extends AnimationClip>> nodes;

        public BlendSpace1DBuilder() {
            nodes = new TreeMap<>();
        }

        public BlendSpace1DBuilder putAnimationNodeAt(boolean loops) {
            this.loops = loops;
            return this;
        }

        public BlendSpace1DBuilder putAnimationNodeAt(float position, AssetAccessor<? extends AnimationClip> animation) {
            nodes.put(position, animation);
            return this;
        }
    }
}
