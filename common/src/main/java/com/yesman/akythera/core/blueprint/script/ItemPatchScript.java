package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/// The events of an [ItemPatch]: a Java subclass registered as a [ScriptType] in
/// [AkytheraRegistries.Frozen#SCRIPT_TYPE], or the graphs of an [ItemPatchBlueprint] run as a
/// [DataDrivenItemPatchScript].
///
/// One script serves every entity holding the item, on both logical sides, so it keeps no state: an event works
/// on the `holder` it is given.
///
/// An event calls [#parent]'s until a subclass overrides it. An override calls `super` to run the parent's too.
public abstract class ItemPatchScript {
    /// The item patch whose events this script runs.
    protected final ItemPatch owner;

    /// The script of the nearest ancestor blueprint that has one. Null when no ancestor has any.
    private final @Nullable ItemPatchScript parent;

    protected ItemPatchScript(final ItemPatch owner, final @Nullable ItemPatchScript parent) {
        this.owner = owner;
        this.parent = parent;
    }

    /// Called when the item enters `slot` of `holder`.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    public void onEquipped(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        if (parent != null) parent.onEquipped(holder, slot);
    }

    /// Called every tick while the item sits in `slot` of `holder`.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    public void onBeingHeld(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        if (parent != null) parent.onBeingHeld(holder, slot);
    }

    /// Called when the item leaves `slot` of `holder`, or `holder` is removed from its level with the item in `slot`.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    public void onUnequipped(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        if (parent != null) parent.onUnequipped(holder, slot);
    }

    /// Whether `type` can run as a Java script: a concrete [ItemPatchScript] declaring a public constructor that
    /// takes its owner [ItemPatch] and its parent [ItemPatchScript].
    public static boolean runnable(final Class<?> type) {
        if (!ItemPatchScript.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) return false;

        try {
            type.getConstructor(ItemPatch.class, ItemPatchScript.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /// A new `type` owned by `itemPatch` over `parent`, through the constructor [#runnable] tests.
    ///
    /// @throws IllegalStateException when the constructor is missing or throws
    public static ItemPatchScript instantiate(final Class<?> type, final ItemPatch itemPatch, final @Nullable ItemPatchScript parent) {
        try {
            return (ItemPatchScript)type.getConstructor(ItemPatch.class, ItemPatchScript.class).newInstance(itemPatch, parent);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed at binding item patch and script " + type.getSimpleName(), e);
        }
    }
}
