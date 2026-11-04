package com.yesman.akythera.core.animation.keyframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Comparator;
import java.util.List;

/// A value over time, set by keys in time order. Between two keys the value follows the earlier key's
/// [Key#smooth]; before the first key and after the last it holds their values.
///
/// @param keys in time order
public record FloatCurve(List<Key> keys) {
    public static final Codec<FloatCurve> CODEC = Key.CODEC.listOf().xmap(FloatCurve::new, FloatCurve::keys);

    public FloatCurve {
        keys = keys.stream().sorted(Comparator.comparingDouble(Key::time)).toList();
    }

    /// The value at `time`, 0 for a curve with no key.
    public float valueAt(final float time) {
        if (keys.isEmpty()) return 0.0F;

        Key first = keys.getFirst();
        Key last = keys.getLast();

        if (time <= first.time()) return first.value();
        if (time >= last.time()) return last.value();

        int next = 1;

        while (keys.get(next).time() < time) next++;

        Key from = keys.get(next - 1);
        Key to = keys.get(next);
        float span = to.time() - from.time();

        if (span <= 0.0F) return to.value();

        float s = (time - from.time()) / span;

        if (!from.smooth()) return from.value() + (to.value() - from.value()) * s;

        float fromTangent = tangentAt(next - 1) * span;
        float toTangent = tangentAt(next) * span;
        float s2 = s * s;
        float s3 = s2 * s;

        return (2.0F * s3 - 3.0F * s2 + 1.0F) * from.value() + (s3 - 2.0F * s2 + s) * fromTangent
            + (-2.0F * s3 + 3.0F * s2) * to.value() + (s3 - s2) * toTangent;
    }

    /// The slope at key `index` from its neighbors, 0 at either end, in value per second.
    private float tangentAt(final int index) {
        if (index == 0 || index == keys.size() - 1) return 0.0F;

        Key before = keys.get(index - 1);
        Key after = keys.get(index + 1);
        float span = after.time() - before.time();

        return span <= 0.0F ? 0.0F : (after.value() - before.value()) / span;
    }

    /// One key of a [FloatCurve].
    ///
    /// @param time   in seconds
    /// @param smooth whether the value eases toward the next key instead of moving at a constant rate
    public record Key(float time, float value, boolean smooth) {
        public static final Codec<Key> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.FLOAT.fieldOf("time").forGetter(Key::time),
                    Codec.FLOAT.fieldOf("value").forGetter(Key::value),
                    Codec.BOOL.optionalFieldOf("smooth", false).forGetter(Key::smooth)
                ).apply(instance, Key::new)
        );
    }
}
