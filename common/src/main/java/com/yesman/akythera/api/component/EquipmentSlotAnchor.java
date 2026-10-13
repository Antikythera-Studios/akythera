package com.yesman.akythera.api.component;

import com.yesman.akythera.api.world.patch.item.ItemPatch;
import net.minecraft.world.entity.EquipmentSlot;

/// A [TransformComponent] the item in [#equipmentSlot] hangs under: the holder's [ItemPatch] for that slot is placed
/// at its [#modelTransform].
public interface EquipmentSlotAnchor extends TransformComponent {
    EquipmentSlot equipmentSlot();
}
