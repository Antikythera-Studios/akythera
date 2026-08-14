package com.yesman.akythera.api.world.patch.item;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
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
    public ItemPatch(final Item original, final Map<String, ? extends PatchComponent> components) {
        this.original = original;
        this.components = Collections.unmodifiableMap(components);

        Map<Class<? extends UniqueComponent>, UniqueComponent> uniqueComponentMap = new HashMap<>();

        for (Map.Entry<String, ? extends PatchComponent> entry : components.entrySet()) {
            if (entry.getValue() instanceof UniqueComponent uc) {
                if (uniqueComponentMap.containsKey(uc.getClass())) {
                    throw new IllegalStateException(
                        MessageFormat.format("""
                            Detected more than one component of {0}, {0} is UniqueComponent that does not allow duplicated types of components. {1} must be removed to properly launch the game.
                        """, uc.getClass(), entry.getKey())
                    );
                }

                uniqueComponentMap.put(uc.getClass(), uc);
            }
        }

        uniqueComponents = Collections.unmodifiableMap(uniqueComponentMap);
    }

    @Override
    public Map<String, ? extends PatchComponent> getComponents() {
        return components;
    }

    @SuppressWarnings("unchecked") @Override
    public <C extends UniqueComponent> C getComponent(final Class<C> type) {
        return (C)uniqueComponents.get(type);
    }

    public Item getOriginal() {
        return original;
    }
}
