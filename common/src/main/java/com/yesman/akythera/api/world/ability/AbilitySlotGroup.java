package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

/// Groups [AbilitySlot]s.
///
/// Akythera declares no entries. Each project registers its own.
public interface AbilitySlotGroup extends EnumerableDataBlock {
    EnumerableDataBlockManager<AbilitySlotGroup> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "ability_slot_group",
        MapCodec.unit(() -> new AbilitySlotGroup() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                return enumName == null ? "AbilitySlotGroup" : enumName.toString();
            }
        })
    );

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
