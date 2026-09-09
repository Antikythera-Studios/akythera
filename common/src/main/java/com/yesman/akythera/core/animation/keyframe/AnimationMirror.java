package com.yesman.akythera.core.animation.keyframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Bakes the left-right reflection of an [AnimationClip].
///
/// A reflection across the X = 0 plane conjugates a transform into `R · A · R`, with
/// `R = diag(-1, 1, 1)`. Componentwise that is: negate the translation's x, negate the rotation
/// quaternion's y and z (the axis reflects and the handedness flips, and the two reduce to that),
/// and leave the scale alone — a diagonal scale describes the same shape on either side.
///
/// Which bone a mirrored track lands on comes from the vendor armature's
/// [ArmatureLoader.MirrorBonePair] table, so a rig is free to name its sides however it likes. A
/// bone with no partner mirrors onto itself, which is what makes root motion and spine sway reflect
/// instead of standing still.
public final class AnimationMirror {
    /// Appended to the source identifier to name a mirrored animation. The asset behind it only
    /// ever exists in memory.
    public static final String IDENTIFIER_SUFFIX = "_autogen_mirror";

    private AnimationMirror() {}

    /// `animationId`'s mirroring declaration as its `.akmeta` carries it, or [Setup#DISABLED] when
    /// the file is absent or names neither key.
    public static Setup readSetup(final Identifier animationId, final ResourceManager resourceManager) {
        return AssetLoader.readMetadata(animationId, resourceManager, Setup.CODEC, Setup.DISABLED);
    }

    /// `source` reflected, with every track moved onto its bone's mirror partner in `vendor`.
    public static AnimationClip mirror(final AnimationClip source, final Armature vendor) {
        AnimationClip.Builder builder = AnimationClip.builder();

        source.visitBoneTracks((boneName, track) -> {
            List<Keyframe> keyframes = new ArrayList<>(track.length());

            track.forEach((_, keyframe) ->
                keyframes.add(new Keyframe(keyframe.timeStamp(), mirror(keyframe.transform())))
            );

            builder.addBoneAnimation(vendor.getMirrorBone(boneName), new BoneAnimationTrack(keyframes));
        });

        return builder.build();
    }

    /// `transform` reflected across the X = 0 plane.
    public static Transform mirror(final Transform transform) {
        Vec3 loc = transform.loc();
        Quaterniond rot = transform.rot();

        return new Transform(
            new Vec3(-loc.x(), loc.y(), loc.z()),
            new Quaterniond(rot.x(), -rot.y(), -rot.z(), rot.w()),
            transform.sca()
        );
    }

    /// The mirroring half of an animation's `.akmeta`.
    ///
    /// @param vendor  the armature the clip was authored against, whose sidecar carries the bone
    ///                pairs. Null when the sidecar names none.
    /// @param enabled `enable_mirroring`
    public record Setup(@Nullable AssetAccessor<? extends Armature> vendor, boolean enabled) {
        public static final Codec<Setup> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ArmatureManager.CODEC.optionalFieldOf("vendor").forGetter(ParseUtils.provideOptional(Setup::vendor)),
                    Codec.BOOL.optionalFieldOf("enable_mirroring", false).forGetter(Setup::enabled)
                ).apply(instance, Setup::new)
        );

        /// What an animation without a sidecar file is read with.
        public static final Setup DISABLED = new Setup((AssetAccessor<? extends Armature>)null, false);

        // Serialization
        public Setup(final Optional<AssetAccessor<? extends Armature>> vendor, final boolean enabled) {
            this(vendor.orElse(null), enabled);
        }

        /// Whether this declaration actually asks for a mirrored clip: the flag is on and the vendor
        /// slot holds a real armature.
        public boolean isMirroring() {
            return enabled && vendor != null && vendor != ArmatureManager.EMPTY_ARMATURE_ACCESSOR;
        }
    }
}
