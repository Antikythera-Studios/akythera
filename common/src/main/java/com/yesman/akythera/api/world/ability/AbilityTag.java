package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// A label on an [AbilitySystemComponent] or an [Ability], matched by path prefix.
///
/// A tag's identity is its path, under one fixed namespace: `akythera:activation/combo_attack` and
/// `epicfight:activation/combo_attack` are the same tag, so an addon extends the base tree rather
/// than forking it.
@ScriptType(autoRegister = false)
public interface AbilityTag extends EnumerableDataBlock {
    EnumerableDataBlockManager<AbilityTag> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "ability_tag", MapCodec.unit(AbilityTag::create), AbilityTag::create
    );

    /// A tag carrying nothing but the path it is registered under.
    private static AbilityTag create() {
        return new AbilityTag() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                return path();
            }
        };
    }

    /// This tag's path, which is its identity. Empty while the tag is not registered.
    default String path() {
        Identifier identifier = ENUM_MANAGER.derefIdentifier(this);

        return identifier == null ? "" : identifier.getPath();
    }

    /// Whether `other` is this tag or sits beneath it.
    ///
    /// `activation` matches `activation/combo_attack`; `activation_extra` does not, since the
    /// match is on whole path segments.
    default boolean matches(final @Nullable AbilityTag other) {
        if (other == null) return false;
        if (other == this) return true;

        String self = path();
        String candidate = other.path();

        if (self.isEmpty() || candidate.isEmpty()) return false;

        return candidate.equals(self) || candidate.startsWith(self + "/");
    }

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
