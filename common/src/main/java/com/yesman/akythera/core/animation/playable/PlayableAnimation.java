package com.yesman.akythera.core.animation.playable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.world.entity.Entity;
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

    /// Raw root-bone transform at `time` — the actual root-motion displacement, NOT zeroed the way
    /// [#getPose] zeros it under root motion. Identity when the animation carries no root track.
    ///
    /// Used by root-motion blending (e.g. [com.yesman.akythera.core.animation.driver.BlendSpace2DDriver]
    /// mixing adjacent nodes) and by the editor's trajectory preview.
    default Transform getRootMotionTransform(final float time) {
        return Transform.IDENTITY;
    }

    /// Ticks the animation in fixed update
    ///
    /// Designed to run owner attached [AnimationNotifyTrack]
    void tick(final EntityPatch<Entity> owner, final float prevElapsedTime, final float currentElapsedTime);

    /// Polymorphic codec for [PlayableAnimation] driven classes. JSON shape:
    ///
    /// ```
    /// { "type": "sequence", "sequence": "akythera:anim2" }
    /// or,
    /// { "type": "montage", "montage": "akythera:anim1" }
    /// ```
    ///
    /// Two branches are supported:
    /// - 'sequence': a data-pack [AnimationSequence] resolved via [AkytheraRegistries.DataPack#ANIM_SEQUENCE_KEY].
    /// ` 'montage': a data-pack [AnimationMontage] resolved via [AkytheraRegistries.DataPack#ANIM_MONTAGE_KEY].
    Codec<Holder<? extends @NonNull PlayableAnimation>> CODEC = Codec.STRING.dispatch(
        "type",
        PlayableAnimation::tagOf,
        PlayableAnimation::codecOf
    );

    private static MapCodec<? extends Holder<? extends @NonNull PlayableAnimation>> codecOf(final String tag) {
        return (
            switch (tag) {
                case "montage" -> RegistryFileCodec.create(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AnimationMontage.CODEC).fieldOf("montage");
                case "sequence" -> RegistryFileCodec.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AnimationSequence.CODEC).fieldOf("sequence");
                default -> throw new IllegalArgumentException("Unknown PlayableAnimation type: '" + tag + "' (expected 'montage' or 'sequence')");
            }
        ).stable();
    }

    private static String tagOf(final Holder<? extends @NonNull PlayableAnimation> playableHolder) {
        if (playableHolder.value() instanceof AnimationMontage) return "montage";
        if (playableHolder.value() instanceof AnimationSequence) return "sequence";

        throw new IllegalStateException("Unsupported PlayableAnimation implementation: " + playableHolder.value().getClass());
    }
}