package com.yesman.akythera.core.animation.playable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/// A simple playable animation with fundamental metadata defined in [PlayableAnimation]
///
/// Objects synchronized by DFU, but not meant to support animation networking. This is
/// for animations that supposed to be played only in client side by client properties.
/// (e.g. Entity locomotion, jump)
///
/// Notify track entries are restricted to [LogicalSide#CLIENT] notifies — sequences are
/// loaded from resource packs and are not server-synced, so server-side notifies would
/// never trigger correctly. Use [AnimationMontage] if you need server-side notifies and
/// proper network handling.
public record AnimationSequence(
    AnimationNotifyTrack notifyTrack,
    AssetAccessor<AnimationClip> animClip,
    float fadeInTime,
    float fadeOutTime,
    boolean loops,
    boolean rootMotion
) implements PlayableAnimation {
    public static final MapCodec<AnimationSequence> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AnimationNotifyTrack.CODEC.optionalFieldOf("notify_track", AnimationNotifyTrack.EMPTY).forGetter(AnimationSequence::notifyTrack),
                AnimationManager.CODEC.fieldOf("animation_path").forGetter(AnimationSequence::animClip),
                Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationSequence::fadeInTime),
                Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationSequence::fadeOutTime),
                Codec.BOOL.fieldOf("loops").forGetter(AnimationSequence::loops),
                Codec.BOOL.fieldOf("root_motion").forGetter(AnimationSequence::rootMotion)
            )
            .apply(instance, AnimationSequence::new)
    );

    public static final Codec<AnimationSequence> CODEC = MAP_CODEC.codec();

    /// Convenience: no notifies, default fade times
    public AnimationSequence(final AssetAccessor<AnimationClip> animClip, final boolean loops, final boolean rootMotion) {
        this(AnimationNotifyTrack.EMPTY, animClip, DEFAULT_FADE_TIME, DEFAULT_FADE_TIME, loops, rootMotion);
    }

    @Override
    public void tick(final EntityPatch<Entity> owner, final float prevElapsedTime, final float currentElapsedTime) {
        notifyTrack.tick(this, owner, prevElapsedTime, currentElapsedTime);
    }

    @Override
    public Pose getPose(final float time) {
        Pose pose = animClip.get().getPose(time);

        // Clear the transform while root motion is activated
        if (rootMotion() && pose.hasTransform(ArmatureLoader.ROOT_BONE)) {
            pose.putBoneTransform(ArmatureLoader.ROOT_BONE, Transform.ZERO);
        }

        return pose;
    }

    @Override
    public float getTotalFrameTime() {
        return animClip.get().getTotalTime();
    }

    @Override
    public Transform getRootMotionTransform(final float time) {
        AnimationClip clip = animClip.get();
        if (clip == null) return Transform.IDENTITY;

        return clip.getPose(Math.clamp(time, 0.0F, clip.getTotalTime()))
            .orElse(ArmatureLoader.ROOT_BONE, Transform.IDENTITY);
    }

    @Override
    public @NonNull String toString() {
        return StaticRegistryAccess.getRegisteredName(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, this);
    }
}
