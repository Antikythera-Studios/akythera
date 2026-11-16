package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/// Runs the [ItemPatchScript] events one [ItemPatchBlueprint] authors. A [ParentCallNode] calls the parent script's
/// event, and so does an event the blueprint leaves out.
///
/// The events are `synchronized`. [AkytheraPatches#getItemPatch] answers one [ItemPatch] per item, so in single
/// player the client thread and the integrated server thread run the same script. The graphs keep no state, but a
/// [BlueprintExecutor] run does: it binds the event's arguments as locals and counts its depth, and two runs at once
/// would read each other's `holder`. The lock is re-entrant, so a graph calling a function of its own blueprint
/// still runs.
public final class DataDrivenItemPatchScript extends ItemPatchScript implements DataDrivenScript<ItemPatch, ItemPatchBlueprint> {
    /// [ScriptCaller] sites of the entry points, which are the events' method names.
    public static final String ON_EQUIPPED = "onEquipped";
    public static final String ON_BEING_HELD = "onBeingHeld";
    public static final String ON_UNEQUIPPED = "onUnequipped";

    private final Holder.Reference<ItemPatchBlueprint> blueprint;

    private final BlueprintExecutor executor;

    public DataDrivenItemPatchScript(
        final Holder.Reference<ItemPatchBlueprint> blueprint, final BlueprintBody body, final ItemPatch owner, final @Nullable ItemPatchScript parent
    ) {
        super(owner, parent);

        this.blueprint = blueprint;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(ItemPatchScript.class), body);
    }

    // ***************************************************************
    // ItemPatchScript
    // ***************************************************************

    @Override
    public synchronized void onEquipped(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        executor.invokeMethod(ON_EQUIPPED, () -> {
            super.onEquipped(holder, slot);
            return null;
        }, holder, slot);
    }

    @Override
    public synchronized void onBeingHeld(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        executor.invokeMethod(ON_BEING_HELD, () -> {
            super.onBeingHeld(holder, slot);
            return null;
        }, holder, slot);
    }

    @Override
    public synchronized void onUnequipped(final LivingEntityPatch<?> holder, final EquipmentSlot slot) {
        executor.invokeMethod(ON_UNEQUIPPED, () -> {
            super.onUnequipped(holder, slot);
            return null;
        }, holder, slot);
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public ItemPatch getScriptOwner() {
        return owner;
    }

    @Override
    public Holder.Reference<ItemPatchBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return executor.variables();
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
