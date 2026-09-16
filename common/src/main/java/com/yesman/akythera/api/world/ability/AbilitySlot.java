package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

/// One equippable slot on an [AbilitySystemComponent]. A slot holds at most one ability, and every
/// give names the slot it fills.
///
/// Akythera declares no entries. Each project registers its own.
public interface AbilitySlot extends EnumerableDataBlock {
    EnumerableDataBlockManager<AbilitySlot> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "ability_slot",
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AbilitySlotGroup.ENUM_MANAGER.getNameCodec().fieldOf("group").forGetter(AbilitySlot::group)
                ).apply(instance, group -> new AbilitySlot() {
                    @Override
                    public AbilitySlotGroup group() {
                        return group;
                    }

                    @Override
                    public int universalOrdinal() {
                        return ENUM_MANAGER.derefOrdinal(this);
                    }

                    @Override
                    public String toString() {
                        Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                        return enumName == null ? "AbilitySlot" : enumName.toString();
                    }
                })
        )
    );

    /// The group this slot belongs to.
    AbilitySlotGroup group();

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
