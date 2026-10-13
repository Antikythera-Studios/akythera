package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// An immutable set of [AbilityTag]s, queried by the path rule of [AbilityTag#matches]: holding
/// `activation/combo_attack` also holds `activation`.
///
/// A parent is implied by its children, so the set keeps only the deepest tags. Adding `activation`
/// next to `activation/combo_attack` changes nothing.
@ScriptType(autoRegister = false)
public final class AbilityTagSet implements Iterable<AbilityTag> {
    public static final AbilityTagSet EMPTY = new AbilityTagSet(Set.of());

    /// A list of tag ids.
    public static final Codec<AbilityTagSet> CODEC = AbilityTag.ENUM_MANAGER.getNameCodec().listOf()
        .xmap(AbilityTagSet::of, set -> List.copyOf(set.tags));

    private final Set<AbilityTag> tags;

    private AbilityTagSet(final Set<AbilityTag> tags) {
        this.tags = tags;
    }

    /// A set of `tags`, less every tag that sits above another of them.
    public static AbilityTagSet of(final Collection<? extends AbilityTag> tags) {
        Set<AbilityTag> deepest = new LinkedHashSet<>();

        for (AbilityTag tag : tags) {
            if (tags.stream().noneMatch(other -> tag.matches(other) && !other.matches(tag))) deepest.add(tag);
        }

        return deepest.isEmpty() ? EMPTY : new AbilityTagSet(Collections.unmodifiableSet(deepest));
    }

    /// The tags this set holds, none of them above another.
    public Set<AbilityTag> tags() {
        return tags;
    }

    @ScriptFunction(pure = true, label = "Is Tag Set Empty", category = "Ability/Tag")
    public boolean isEmpty() {
        return tags.isEmpty();
    }

    /// Whether this set holds `query` or anything beneath it.
    @ScriptFunction(pure = true, label = "Has Tag", category = "Ability/Tag", desc = "True while the set holds the tag or anything beneath it.")
    public boolean hasTag(final AbilityTag query) {
        for (AbilityTag tag : tags) {
            if (query.matches(tag)) return true;
        }

        return false;
    }

    /// Whether this set holds `query` itself, not only something beneath it.
    @ScriptFunction(pure = true, label = "Has Tag Exact", category = "Ability/Tag", desc = "True while the set holds the tag itself.")
    public boolean hasTagExact(final AbilityTag query) {
        return tags.contains(query);
    }

    /// Whether this set holds any of `queries`, by [#hasTag]. False for no queries.
    @ScriptFunction(pure = true, label = "Has Any Tags", category = "Ability/Tag", desc = "True while the set holds any of the tags.")
    public boolean hasAny(final AbilityTagSet queries) {
        for (AbilityTag query : queries.tags) {
            if (hasTag(query)) return true;
        }

        return false;
    }

    /// Whether this set holds every one of `queries`, by [#hasTag]. True for no queries.
    @ScriptFunction(pure = true, label = "Has All Tags", category = "Ability/Tag", desc = "True while the set holds every one of the tags.")
    public boolean hasAll(final AbilityTagSet queries) {
        for (AbilityTag query : queries.tags) {
            if (!hasTag(query)) return false;
        }

        return true;
    }

    @Override
    public @NonNull Iterator<AbilityTag> iterator() {
        return tags.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof AbilityTagSet other && tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return tags.toString();
    }
}
