package com.yesman.akythera.core.animation.playable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/// A set of keyframe and pose data managed by [AnimationClip].
///
/// Abstraction of modules that returns [Pose] in a given timestamp.
public interface PlayableAnimation {
    /// An empty animation sequence holder for various purposes
    Holder<@NonNull AnimationSequence> EMPTY_ANIM_HOLDER = Holder.direct(
        new AnimationSequence(AnimationManager.EMPTY_ANIMATION_ACCESSOR, false, false)
    );

    float DEFAULT_FADE_TIME = 0.25F;

    /// Returns a pose in a corresponding timeframe
    Pose getPose(final float time);

    /// Pose blending time when montage starts to play
    float fadeInTime();

    /// Pose blending time when montage terminates
    float fadeOutTime();

    /// Returns a max frame time of animation
    float getTotalFrameTime();

    /// Returns whether the animation should loop when it ends
    boolean loops();

    default float rateScale() {
        return 1.0F;
    }

    default float phaseOffset() {
        return 0.0F;
    }

    /// Raw root-bone transform at `time` — the actual root-motion displacement, NOT zeroed the way
    /// [#getPose] zeros it under root motion. Identity when the animation carries no root track.
    ///
    /// Used by root-motion blending (e.g. [com.yesman.akythera.core.animation.driver.BlendSpace2DDriver]
    /// mixing adjacent nodes) and by the editor's trajectory preview.
    default Transform getRootMotionTransform(final float time) {
        return Transform.IDENTITY;
    }

    /// This animation over the mirrored twin of its clips, see [AnimationManager#getMirrorAnimation].
    /// An animation with no mirrored form answers itself.
    default PlayableAnimation mirrored() {
        return this;
    }

    /// Ticks the animation in fixed update
    ///
    /// Designed to run owner attached [AnimationNotifyTrack]
    void tick(final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime);

    /// Runs the owner attached [AnimationNotifyTrack]'s notifies a frame checks, see [AnimationNotifyTrack#frameUpdate]
    void frameUpdate(final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime);

    /// Ends the notify states the playback sits inside, for a play stopped there
    ///
    /// @param lastTickTime  the time the last [#tick] ran to
    /// @param lastFrameTime the time the last [#frameUpdate] ran to
    void interrupt(final ComponentAttachable owner, final float lastTickTime, final float lastFrameTime);

    /// Polymorphic codec for a **reference** to a [PlayableAnimation] entry. JSON shape:
    ///
    /// ```
    /// { "type": "sequence", "sequence": "akythera:anim2" }
    /// or,
    /// { "type": "blend_space_2d", "blend_space_2d": "akythera:locomotion" }
    /// ```
    ///
    /// A bare id string is shorthand for a sequence.
    Codec<ResourceKey<? extends @NonNull PlayableAnimation>> KEY_CODEC = Codec.withAlternative(
        Codec.STRING.dispatch("type", PlayableAnimation::keyTagOf, PlayableAnimation::keyCodecOf),
        ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY)
    );

    /// Network form of [#KEY_CODEC], dispatched on the same tags.
    StreamCodec<ByteBuf, ResourceKey<? extends @NonNull PlayableAnimation>> KEY_STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.dispatch(PlayableAnimation::keyTagOf, PlayableAnimation::keyStreamCodecOf);

    private static String keyTagOf(final ResourceKey<? extends @NonNull PlayableAnimation> animationKey) {
        if (animationKey.isFor(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY)) return "sequence";
        if (animationKey.isFor(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY)) return "blend_space_2d";

        throw new IllegalStateException("Unsupported PlayableAnimation registry: " + animationKey.registry());
    }

    private static MapCodec<? extends ResourceKey<? extends @NonNull PlayableAnimation>> keyCodecOf(final String tag) {
        return switch (tag) {
            case "sequence" -> ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).fieldOf("sequence");
            case "blend_space_2d" -> ResourceKey.codec(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY).fieldOf("blend_space_2d");
            default -> throw new IllegalArgumentException(
                "Unknown PlayableAnimation reference type: '" + tag + "' (expected 'sequence' or 'blend_space_2d')");
        };
    }

    private static StreamCodec<? super ByteBuf, ? extends ResourceKey<? extends @NonNull PlayableAnimation>> keyStreamCodecOf(
        final String tag
    ) {
        return switch (tag) {
            case "sequence" -> ResourceKey.streamCodec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY);
            case "blend_space_2d" -> ResourceKey.streamCodec(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY);
            default -> throw new IllegalArgumentException(
                "Unknown PlayableAnimation reference type: '" + tag + "' (expected 'sequence' or 'blend_space_2d')");
        };
    }
}