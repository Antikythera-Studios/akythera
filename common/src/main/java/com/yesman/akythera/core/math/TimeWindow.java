package com.yesman.akythera.core.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// A utility to define a time period
public final class TimeWindow implements Comparable<TimeWindow> {
    public static final Codec<TimeWindow> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.FLOAT.listOf(2, 2).fieldOf("time_pair").forGetter(timeWindow -> List.of(timeWindow.from(), timeWindow.to()))
            )
            .apply(instance, (list) -> new TimeWindow(list.get(0), list.get(1)))
    );

    public static TimeWindow create(final float t1, final float t2) {
        return new TimeWindow(t1, t2);
    }

    /// Compared by great equal, meaning that if a given time exactly same as [#from], it overlaps.
    private final float from;

    /// Calculated by less, meaning that if a given time exactly same as [#to], it doesn't overlap.
    private final float to;

    /// @param from     when the window starts
    /// @param to       when the window ends
    ///
    /// from must be less than to
    private TimeWindow(final float from, final float to) {
        this.from = Math.min(from, to);
        this.to = Math.max(from, to);
    }

    public float from() {
        return from;
    }

    public float to() {
        return to;
    }

    /// Compare by `from`, since `to` is ignorable in ordering
    @Override
    public int compareTo(final @NonNull TimeWindow o) {
        return Float.compare(from, o.from);
    }

    /// Returns whether given time is in window
    public boolean isIn(final float t) {
        return t >= from && t < to;
    }

    public boolean windowOverlaps(final TimeWindow other) {
        return windowOverlaps(other.from(), other.to());
    }

    /// `from` must be less than `to`
    public boolean windowOverlaps(final float from, final float to) {
        assert from <= to;
        return !(this.from > to || this.to < from);
    }
}
