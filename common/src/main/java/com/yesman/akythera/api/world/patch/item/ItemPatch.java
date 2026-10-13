package com.yesman.akythera.api.world.patch.item;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.EquipmentSlotAnchor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/// An item plugin for extra data used by Epic Fight
///
/// Item patches can depend on other patches. For example, sword items have material
/// variations like wooden, stone, golden, etc. A blueprint called "sword" has core
/// components applied to all variations, and each variation declares
/// [ItemPatchBlueprint#parent()] to the "sword" blueprint, overriding or adding its own
/// components on top.
///
/// Patches are assembled from [ItemPatchBlueprint] on the first reference after a registry
/// access is loaded. Check details in [AkytheraPatches#getItemPatch].
///
/// The patch [AkytheraPatches#getItemPatch] answers is static and shared through all [ItemStack], so no
/// [DynamicPatchComponent] is allowed to be here. An [EquipmentHolder] holds its own instance per equipment slot,
/// built from the same blueprint, whose components are its own.
@ScriptType
public final class ItemPatch implements ComponentAttachable {
    private final Item original;

    private final ItemPatchBlueprint blueprint;

    /// The entity holding this instance in one of its equipment slots, or null for the shared patch.
    private final @Nullable EntityPatch<?> holder;

    /// The slot [#holder] holds this instance in, or null for the shared patch.
    private final @Nullable EquipmentSlot slot;
    private final Map<String, ? extends PatchComponent> components;

    /// Components by type. Each component is unique in the patch
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    /// Instantiated only by [ItemPatchBlueprint#instantiate]
    @ApiStatus.Internal
    public ItemPatch(
        final Item original,
        final ItemPatchBlueprint blueprint,
        final @Nullable EntityPatch<?> holder,
        final @Nullable EquipmentSlot slot
    ) {
        this.original = original;
        this.blueprint = blueprint;
        this.holder = holder;
        this.slot = slot;
        this.components = Collections.unmodifiableMap(ItemPatchBlueprint.assembleComponents(blueprint, this));
        this.uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// The patch's components, in the order the blueprint hierarchy declared them. A consumer that
    /// picks the **first** matching component reads this order as priority order.
    @Override
    public Map<String, ? extends PatchComponent> getComponents() {
        return components;
    }

    /// The shared patch serves both sides; a holder's instance lives on its holder's side.
    @Override
    public LogicalSide logicalSide() {
        return holder == null ? LogicalSide.BOTH : holder.logicalSide();
    }

    @SuppressWarnings("unchecked") @Override
    public <C extends UniqueComponent> C getComponent(final Class<C> type) {
        UniqueComponent component = uniqueComponents.get(type);

        return component != null && component.isEnabled() ? (C)component : null;
    }

    /// The [WeaponStanceVariationAdapter] whose condition `equipper` establishes while holding this
    /// item in `slot` — the first one [#getComponents] lists that matches, so component order is
    /// stance priority order. Null when none does.
    public @Nullable WeaponStanceVariationAdapter getStanceAdapter(final LivingEntityPatch<?> equipper, final EquipmentSlot slot) {
        for (PatchComponent component : components.values()) {
            if (component.isEnabled()
                && component instanceof WeaponStanceVariationAdapter adapter
                && adapter.matches(equipper, slot)) {
                return adapter;
            }
        }

        return null;
    }

    public Item getOriginal() {
        return original;
    }

    public ItemPatchBlueprint getBlueprint() {
        return blueprint;
    }

    /// The entity holding this instance, or null for the shared patch.
    public @Nullable EntityPatch<?> getHolder() {
        return holder;
    }

    /// The slot the holder holds this instance in, or null for the shared patch.
    public @Nullable EquipmentSlot getSlot() {
        return slot;
    }

    /// The component this instance hangs under in its holder's component tree: the first switched-on
    /// [EquipmentSlotAnchor] drawing [#slot]. Null for the shared patch, and for a holder drawing the slot nowhere.
    public @Nullable EquipmentSlotAnchor getAnchor() {
        if (holder == null || slot == null) return null;

        return holder.findComponentBelow(EquipmentSlotAnchor.class, anchor -> anchor.equipmentSlot() == slot);
    }
}
