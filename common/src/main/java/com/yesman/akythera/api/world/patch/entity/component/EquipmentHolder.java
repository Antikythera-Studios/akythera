package com.yesman.akythera.api.world.patch.entity.component;

import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
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

    /// Returns the [ItemPatch] of the item in specific equipment slot. Null if the item has no patch.
    @Nullable ItemPatch getItemPatchInSlot(EquipmentSlot equipmentSlot);

    /// Returns the switched-on [EquipmentSlotBinder] of specific equipment slot, which attaches that item patch's
    /// [TransformComponent]s beside itself. Null while none binds the slot.
    @Nullable EquipmentSlotBinder getSlotBinder(EquipmentSlot equipmentSlot);

    /// Conventional access for [ItemPatch] in [EquipmentSlot#MAINHAND]
    @Nullable ItemPatch getMainHandItemPatch();

    /// Conventional access for [ItemPatch] in [EquipmentSlot#OFFHAND]
    @Nullable ItemPatch getOffHandItemPatch();
}
