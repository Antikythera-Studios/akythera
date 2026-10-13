package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Comparator;
import java.util.List;

/// The keys of one animated emitter channel, sorted by time.
///
/// @param <T> the channel's value
public record KeyframeTrack<T>(List<Key<T>> keys) {
    public KeyframeTrack {
        keys = keys.stream().sorted(Comparator.comparingDouble(Key::time)).toList();
    }

    /// A track written as a list of keys, in any order.
    public static <T> Codec<KeyframeTrack<T>> codec(final Codec<T> value) {
        return Key.codec(value).listOf().xmap(KeyframeTrack::new, KeyframeTrack::keys);
    }

    public static <T> KeyframeTrack<T> empty() {
        return new KeyframeTrack<>(List.of());
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /// The value at `time`: `rest` with no keys, the first key's before it, the last key's after it, and between two
    /// keys what `interpolator` makes of them.
    public T sample(final float time, final T rest, final Interpolator<T> interpolator) {
        if (keys.isEmpty()) return rest;
        if (time <= keys.getFirst().time) return keys.getFirst().value;
        if (time >= keys.getLast().time) return keys.getLast().value;

        int after = 1;

        while (keys.get(after).time < time) after++;

        Key<T> from = keys.get(after - 1);
        Key<T> to = keys.get(after);

        return interpolator.interpolate(from.value, to.value, (time - from.time) / (to.time - from.time));
    }

    /// @param time in seconds since the emitter's time span started
    public record Key<T>(float time, T value) {
        public static <T> Codec<Key<T>> codec(final Codec<T> value) {
            return RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("time").forGetter(Key::time),
                        value.fieldOf("value").forGetter(Key::value)
                    ).apply(instance, Key::new)
            );
        }
    }

    @FunctionalInterface
    public interface Interpolator<T> {
        /// @param delta 0 at `from`, 1 at `to`
        T interpolate(T from, T to, float delta);
    }
}
