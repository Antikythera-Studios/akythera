package com.yesman.akythera.api.world.patch.item;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
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
/// Note that item patches are static and shared through all [ItemStack], so no
/// [DynamicPatchComponent] is allowed to be here.
public final class ItemPatch implements ComponentAttachable {
    private final Item original;
    private final Map<String, ? extends PatchComponent> components;

    /// Components by type. Each component is unique in the patch
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    /// Instantiated only by [ItemPatchBlueprint#instantiate]
    @ApiStatus.Internal
    public ItemPatch(final Item original, final ItemPatchBlueprint blueprint) {
        this.original = original;
        this.components = Collections.unmodifiableMap(ItemPatchBlueprint.assembleComponents(blueprint, this));
        this.uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// The patch's components, in the order the blueprint hierarchy declared them. A consumer that
    /// picks the **first** matching component reads this order as priority order.
    @Override
    public Map<String, ? extends PatchComponent> getComponents() {
        return components;
    }

    @SuppressWarnings("unchecked") @Override
    public <C extends UniqueComponent> C getComponent(final Class<C> type) {
        return (C)uniqueComponents.get(type);
    }

    /// The [WeaponStanceVariationAdapter] whose condition `equipper` establishes while holding this
    /// item in `slot` — the first one [#getComponents] lists that matches, so component order is
    /// stance priority order. Null when none does.
    public @Nullable WeaponStanceVariationAdapter getStanceAdapter(final LivingEntityPatch<?> equipper, final EquipmentSlot slot) {
        for (PatchComponent component : components.values()) {
            if (component instanceof WeaponStanceVariationAdapter adapter && adapter.matches(equipper, slot)) {
                return adapter;
            }
        }

        return null;
    }

    public Item getOriginal() {
        return original;
    }
}
