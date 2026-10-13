package com.yesman.akythera.api.world.patch.entity.component;

import com.yesman.akythera.api.world.patch.item.ItemPatch;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/// An abstraction of entities practically uses [LivingEntity#equipment].
///
/// Resolves the universalized equipment throughout [LivingEntity]. This interface
/// only tags entities that the equipments slots are actually used.
public interface EquipmentHolder {
    /// Returns [ItemStack] in specific equipment slot. Returns [Items#AIR] if empty
    ItemStack getItemInSlot(EquipmentSlot equipmentSlot);

    /// Returns this holder's own [ItemPatch] instance for the item in specific equipment slot, rebuilt when the
    /// item changes. Null if the item has no patch.
    @Nullable ItemPatch getItemPatchInSlot(EquipmentSlot equipmentSlot);

    /// Conventional access for [ItemPatch] in [EquipmentSlot#MAINHAND]
    @Nullable ItemPatch getMainHandItemPatch();

    /// Conventional access for [ItemPatch] in [EquipmentSlot#OFFHAND]
    @Nullable ItemPatch getOffHandItemPatch();
}
