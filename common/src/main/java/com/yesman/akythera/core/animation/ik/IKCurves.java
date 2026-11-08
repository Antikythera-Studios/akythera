package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.FloatCurve;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/// The curves an animation clip drives one IK chain with under [AkytheraIKType#ANIMATION_CURVE], keyed by the chain's
/// tip bone.
///
/// @param alpha mix of the animated tip (0) and the IK tip (1); absent reads as 1
/// @param plant 1 holds the foot on the ground where it stood when the curve reached 1, 0 lets it follow the animation;
///              absent reads as 0
public record IKCurves(@Nullable FloatCurve alpha, @Nullable FloatCurve plant) {
    public static final Codec<IKCurves> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                FloatCurve.CODEC.optionalFieldOf("ik_alpha").forGetter(ParseUtils.provideOptional(IKCurves::alpha)),
                FloatCurve.CODEC.optionalFieldOf("plant").forGetter(ParseUtils.provideOptional(IKCurves::plant))
            ).apply(instance, IKCurves::new)
    );

    /// How often [#detectPlant] samples a clip, per second.
    private static final float SAMPLE_RATE = 60.0F;

    /// Highest a tip stands above its lowest point and still touches the ground, as a part of its height range.
    private static final float CONTACT_HEIGHT = 0.2F;

    /// Part of the time between two contacts over which a lifted tip goes back to the animation.
    private static final float RELEASE = 0.25F;

    // Serialization
    public IKCurves(final Optional<FloatCurve> alpha, final Optional<FloatCurve> plant) {
        this(alpha.orElse(null), plant.orElse(null));
    }

    /// Whether neither curve is set.
    public boolean isEmpty() {
        return alpha == null && plant == null;
    }

    /// The chain's channel at `time`, with the missing curves at their defaults.
    public IKChannel channelAt(final float time) {
        return new IKChannel(alpha == null ? 1.0F : alpha.valueAt(time), plant == null ? 0.0F : plant.valueAt(time), 0.0F);
    }

    /// A [#plant] curve from the foot contacts of the looping `clip`, null when `armature` has no `tipBone` or the tip
    /// never lifts.
    public static @Nullable FloatCurve detectPlant(final AnimationClip clip, final AssetAccessor<? extends Armature> armature, final String tipBone) {
        Armature resolved = armature.get();
        Bone tip = resolved == null ? null : resolved.getBoneByName(tipBone);
        float total = clip.getTotalTime();

        if (tip == null || total <= 0.0F) return null;

        int samples = Math.max(2, Math.round(total * SAMPLE_RATE));
        float interval = total / samples;
        float[] heights = new float[samples];
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;

        for (int i = 0; i < samples; i++) {
            Matrix4f[] bound = clip.getPose(i * interval).getForwardBoundTransforms(armature);
            heights[i] = bound[tip.getId()].transformPosition(new Vector3f(tip.getToTail())).y();
            lowest = Math.min(lowest, heights[i]);
            highest = Math.max(highest, heights[i]);
        }

        if (highest - lowest < 1.0E-4F) return null;

        float contactHeight = lowest + CONTACT_HEIGHT * (highest - lowest);
        int lifted = 0;

        while (heights[lifted] <= contactHeight) lifted++;

        // Contacts as {touch down, lift off} in seconds
        List<float[]> contacts = new ArrayList<>();

        for (int i = lifted + 1; i <= lifted + samples; i++) {
            boolean touching = heights[i % samples] <= contactHeight;

            if (touching && heights[(i - 1) % samples] > contactHeight) contacts.add(new float[] { i * interval, 0.0F });
            if (!touching && heights[(i - 1) % samples] <= contactHeight) contacts.getLast()[1] = i * interval;
        }

        TreeSet<Float> times = new TreeSet<>(List.of(0.0F, total));
        float[] releases = new float[contacts.size()];

        for (int c = 0; c < contacts.size(); c++) {
            float[] contact = contacts.get(c);
            float nextTouch = contacts.get((c + 1) % contacts.size())[0] + (c + 1 == contacts.size() ? total : 0.0F);
            releases[c] = RELEASE * (nextTouch - contact[1]);

            for (float shift : new float[] { -total, 0.0F, total }) {
                for (float time : new float[] { contact[0] - interval, contact[0], contact[1], contact[1] + releases[c] }) {
                    if (time + shift > interval * 0.5F && time + shift < total - interval * 0.5F) times.add(time + shift);
                }
            }
        }

        List<FloatCurve.Key> keys = new ArrayList<>();

        for (float time : times) {
            float value = 0.0F;

            for (int c = 0; c < contacts.size(); c++) {
                float[] contact = contacts.get(c);

                for (float shift : new float[] { -total, 0.0F, total }) {
                    float local = time - shift;

                    if (local >= contact[0] && local <= contact[1]) {
                        value = 1.0F;
                    } else if (local > contact[0] - interval && local < contact[0]) {
                        value = Math.max(value, (local - contact[0] + interval) / interval);
                    } else if (local > contact[1] && local < contact[1] + releases[c]) {
                        value = Math.max(value, 1.0F - (local - contact[1]) / releases[c]);
                    }
                }
            }

            keys.add(new FloatCurve.Key(time, value, false));
        }

        return new FloatCurve(keys);
    }
}
