package com.yesman.akythera.core.math.algorithm;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.core.util.helper.MathUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/// A data structure for ordered objects, helping the nearest front and behind
/// values via binary searching algorithm.
///
/// Designed to preloaded on the game launches, not recommended for real-time variable use cases.
public final class BinarySearchList<T, V extends Number & Comparable<V>> {
    /// Null when [#isIdentity] is true
    @Nullable
    private final List<T> objectsList;
    private final List<V> valuesList;
    private final boolean isIdentity;

    public BinarySearchList(Builder<T, V> builder) {
        isIdentity = builder.isIdentity;
        ImmutableList.Builder<@NonNull T> objectsListBuilder = ImmutableList.builder();
        ImmutableList.Builder<@NonNull V> valuesListBuilder = ImmutableList.builder();

        // Iterates by order
        for (Map.Entry<V, T> entry : builder.objects.entrySet()) {
            objectsListBuilder.add(entry.getValue());
            valuesListBuilder.add(entry.getKey());
        }

        // Save object list only when it's not an identity mapping
        if (!builder.isIdentity) objectsList = objectsListBuilder.build();
        else objectsList = null;

        valuesList = valuesListBuilder.build();
    }

    /// Returns a nearest first end index
    public Pair<Integer, Integer> getFrontAndBackIndices(V basis) {
        // Binary search
        int begin = 0, end = valuesList.size() - 1;
        Comparator<V> comparator = Comparator.naturalOrder();

        while (end - begin > 1) {
            final int i = begin + (end - begin) / 2;
            final V front = valuesList.get(i);
            final V behind = valuesList.get(i+1);

            if (MathUtils.lessEqual(comparator, front, basis) && MathUtils.greater(comparator, behind, basis)) {
                begin = i;
                end = i+1;
                break;
            } else {
                if (MathUtils.greater(comparator, front, basis)) {
                    end = i;
                } else if (MathUtils.lessEqual(comparator, behind, basis)) {
                    begin = i;
                }
            }
        }

        return Pair.of(begin, end);
    }

    /// Returns object in given index
    @SuppressWarnings("unchecked")
    public T getObject(final int index) {
        return isIdentity ? (T)valuesList.get(index) : objectsList.get(index);
    }

    /// Returns value in given index
    public V getValue(final int index) {
        return valuesList.get(index);
    }

    public static <T, V extends Number & Comparable<V>> Builder<T, V> builder(final Function<T, V> object2Value, boolean isIdentity) {
        return new Builder<>(object2Value, isIdentity);
    }

    public static final class Builder<T, V extends Number & Comparable<V>> {
        private final Function<T, V> object2Value;
        private final TreeMap<V, T> objects = new TreeMap<>(Comparator.naturalOrder());
        private final boolean isIdentity;

        /// @param isIdentity set true when object mapped into itself, [Function#identity]
        private Builder(final Function<T, V> object2Value, boolean isIdentity) {
            this.object2Value = object2Value;
            this.isIdentity = isIdentity;
        }

        public Builder<T, V> add(T object) {
            objects.put(object2Value.apply(object), object);
            return this;
        }

        public Builder<T, V> addAll(Collection<T> object) {
            for (T t : object) {
                objects.put(object2Value.apply(t), t);
            }

            return this;
        }

        public BinarySearchList<T, V> create() {
            return new BinarySearchList<>(this);
        }
    }
}
