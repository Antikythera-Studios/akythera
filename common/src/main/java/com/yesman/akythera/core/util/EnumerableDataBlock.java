package com.yesman.akythera.core.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/// Enums that exposes inheritance
public interface EnumerableDataBlock {
    /// Returns the global ordinal. Beware that the ordinal varies depending on installed addons so you must not use
    /// ordinal id as identifying enums in data files (.json). Instead, use [#identifier]
    int universalOrdinal();

    /// Returns enum manager of this object
    EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager();

    /// Returns enum identifier.
    ///
    /// To avoid name crashes among addons, use identifier instead of [Enum#name()]
    default Identifier identifier() {
        return getEnumManager().derefIdentifier(this);
    }

    default Component toTranslatable() {
        return getEnumManager().toTranslatable(this);
    }
}