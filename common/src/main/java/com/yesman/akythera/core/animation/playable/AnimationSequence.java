package com.yesman.akythera.core.animation.playable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.ik.AkytheraIKType;
import com.yesman.akythera.core.animation.ik.IKType;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.util.ExtraCodecs;
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
///
/// @param moveVertical whether [#rootMotion] moves the entity along the y axis
/// @param ikType       how the IK chains are placed while the sequence plays
public record AnimationSequence(
    AnimationNotifyTrack notifyTrack,
    AssetAccessor<AnimationClip> animClip,
    float fadeInTime,
    float fadeOutTime,
    boolean loops,
    boolean rootMotion,
    boolean moveVertical,
    float playRate,
    float phaseOffset,
    IKType ikType
) implements PlayableAnimation {
    public static final MapCodec<AnimationSequence> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AnimationNotifyTrack.CODEC.optionalFieldOf("notify_track", AnimationNotifyTrack.EMPTY).forGetter(AnimationSequence::notifyTrack),
                AnimationManager.CODEC.fieldOf("animation_path").forGetter(AnimationSequence::animClip),
                Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationSequence::fadeInTime),
                Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationSequence::fadeOutTime),
                Codec.BOOL.fieldOf("loops").forGetter(AnimationSequence::loops),
                Codec.BOOL.fieldOf("root_motion").forGetter(AnimationSequence::rootMotion),
                Codec.BOOL.optionalFieldOf("move_vertical", true).forGetter(AnimationSequence::moveVertical),
                ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("play_rate", 1.0F).forGetter(AnimationSequence::playRate),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("phase_offset", 0.0F).forGetter(AnimationSequence::phaseOffset),
                IKType.ENUM_MANAGER.optionalFieldOf("ik_type", () -> AkytheraIKType.PROCEDURAL).forGetter(AnimationSequence::ikType)
            )
            .apply(instance, AnimationSequence::new)
    );

    public static final Codec<AnimationSequence> CODEC = MAP_CODEC.codec();

    /// Convenience: no notifies, default fade times
    public AnimationSequence(final AssetAccessor<AnimationClip> animClip, final boolean loops, final boolean rootMotion) {
        this(AnimationNotifyTrack.EMPTY, animClip, DEFAULT_FADE_TIME, DEFAULT_FADE_TIME, loops, rootMotion, true, 1.0F, 0.0F, AkytheraIKType.PROCEDURAL);
    }

    public AnimationSequence(
        final AnimationNotifyTrack notifyTrack, final AssetAccessor<AnimationClip> animClip, final float fadeInTime,
        final float fadeOutTime, final boolean loops, final boolean rootMotion
    ) {
        this(notifyTrack, animClip, fadeInTime, fadeOutTime, loops, rootMotion, true, 1.0F, 0.0F, AkytheraIKType.PROCEDURAL);
    }

    /// Convenience: procedural IK
    public AnimationSequence(
        final AnimationNotifyTrack notifyTrack, final AssetAccessor<AnimationClip> animClip, final float fadeInTime,
        final float fadeOutTime, final boolean loops, final boolean rootMotion, final boolean moveVertical, final float playRate,
        final float phaseOffset
    ) {
        this(notifyTrack, animClip, fadeInTime, fadeOutTime, loops, rootMotion, moveVertical, playRate, phaseOffset, AkytheraIKType.PROCEDURAL);
    }

    /// This sequence over the mirrored twin of its clip, see [AnimationManager#getMirrorAnimation].
    /// A clip whose `.akmeta` leaves mirroring off answers itself, so the twin equals this sequence.
    @Override
    public AnimationSequence mirrored() {
        return new AnimationSequence(
            notifyTrack, AnimationManager.getMirrorAnimation(animClip), fadeInTime, fadeOutTime, loops, rootMotion, moveVertical, playRate, phaseOffset,
            ikType
        );
    }

    @Override
    public void tick(final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime) {
        notifyTrack.tick(this, owner, prevElapsedTime, currentElapsedTime);
    }

    @Override
    public void frameUpdate(final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime) {
        notifyTrack.frameUpdate(this, owner, prevElapsedTime, currentElapsedTime);
    }

    @Override
    public void interrupt(final ComponentAttachable owner, final float lastTickTime, final float lastFrameTime) {
        notifyTrack.interrupt(this, owner, lastTickTime, lastFrameTime);
    }

    @Override
    public Pose getPose(final float time) {
        AnimationClip clip = animClip.get();
        Pose pose = clip.getPose(time);
        pose.setIKChannels(ikType.channels(clip, time));

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
