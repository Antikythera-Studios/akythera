package com.yesman.akythera.core.animation.keyframe;

import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

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

    /// `source` reflected, with every track and IK curve moved onto its bone's mirror partner in `vendor`.
    public static AnimationClip mirror(final AnimationClip source, final Armature vendor) {
        AnimationClip.Builder builder = AnimationClip.builder();

        source.visitBoneTracks((boneName, track) -> {
            List<Keyframe> keyframes = new ArrayList<>(track.length());

            track.forEach((_, keyframe) ->
                keyframes.add(new Keyframe(keyframe.timeStamp(), mirror(keyframe.transform())))
            );

            builder.addBoneAnimation(vendor.getMirrorBone(boneName), new BoneAnimationTrack(keyframes));
        });

        source.getIKCurves().forEach((tipBone, curves) -> builder.putIKCurves(vendor.getMirrorBone(tipBone), curves));

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
}
