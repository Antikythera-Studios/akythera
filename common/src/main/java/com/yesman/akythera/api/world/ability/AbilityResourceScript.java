package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import net.minecraft.world.entity.ai.attributes.Attribute;

/// One live value an [AbilityEffect] can read and write that is not an [Attribute]: health, stamina, a weapon charge.
///
/// Built from an [AbilityResourceBlueprint] by its parent constructor, one per entry, shared by every owner.
public interface AbilityResourceScript {
    /// The target's current value.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    float read(final LivingEntityPatch<?> owner);

    /// Writes `value` onto the target, through the API that owns it.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    void write(final LivingEntityPatch<?> owner, final float value);

    /// `value` bounded to what the target can actually hold.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    float clamp(final LivingEntityPatch<?> owner, final float value);

    /// Whether this entry means anything for `owner` at all.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    boolean appliesTo(final LivingEntityPatch<?> owner);

    /// Whether the resource banks charges over time or is spent outright.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    default Kind kind() {
        return Kind.CONSUMPTIVE;
    }

    /// Whether this resource may be spent down to zero.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.PROPERTY)
    default boolean canEmpty() {
        return true;
    }

    /// Whether the owner holds enough to pay `amount`. A resource that cannot be emptied needs strictly more than the
    /// cost. Consumes nothing.
    ///
    /// Runs on both sides: a cost is validated on the client and on the server.
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean hasResource(final LivingEntityPatch<?> owner, final float amount) {
        if (!appliesTo(owner)) return false;

        return canEmpty() ? read(owner) >= amount : read(owner) > amount;
    }

    /// Spends `amount`, leaving the value untouched when the owner holds less than that.
    ///
    /// Server side only.
    ///
    /// @return whether the amount was spent
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default boolean consume(final LivingEntityPatch<?> owner, final float amount) {
        if (!hasResource(owner, amount)) return false;

        add(owner, -amount);

        return true;
    }

    /// Moves the value by `amount`, bounded by [#clamp].
    ///
    /// @return the delta actually applied, which differs from `amount` when the value was clamped
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.FUNCTION)
    default float add(final LivingEntityPatch<?> owner, final float amount) {
        if (!appliesTo(owner)) return 0.0F;

        float before = read(owner);
        float after = clamp(owner, before + amount);

        write(owner, after);

        return after - before;
    }

    /// Whether a resource banks charges over time or is spent outright.
    enum Kind {
        /// Fills up over time or on an event, and can bank charges: a cooldown, a weapon charge.
        ACCUMULATIVE,

        /// Spent on activation and never banks charges: stamina, health.
        CONSUMPTIVE
    }
}
