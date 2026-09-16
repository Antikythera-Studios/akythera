package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import net.minecraft.world.entity.ai.attributes.Attribute;

/// One live value an [AbilityEffect] can read and write that is not an
/// [Attribute]: health, stamina, a weapon charge.

public interface AbilityResource {
    /// The target's current value.
    float read(final LivingEntityPatch<?> owner);

    /// Writes `value` onto the target, through the API that owns it.
    void write(final LivingEntityPatch<?> owner, final float value);

    /// `value` bounded to what the target can actually hold.
    float clamp(final LivingEntityPatch<?> owner, final float value);

    /// Whether this entry means anything for `owner` at all.
    boolean appliesTo(final LivingEntityPatch<?> owner);

    Kind kind();

    /// Whether this resource may be spent down to zero.
    default boolean canEmpty() {
        return true;
    }

    /// Whether the owner holds enough to pay `amount`. A resource that cannot be emptied needs
    /// strictly more than the cost. Consumes nothing.
    ///
    /// Runs on both sides: a cost is validated on the client and on the server.
    default boolean hasResource(final LivingEntityPatch<?> owner, final float amount) {
        if (!appliesTo(owner)) return false;

        return canEmpty() ? read(owner) >= amount : read(owner) > amount;
    }

    /// Spends `amount`, leaving the value untouched when the owner holds less than that.
    ///
    /// Server side only.
    ///
    /// @return whether the amount was spent
    default boolean consume(final LivingEntityPatch<?> owner, final float amount) {
        if (!hasResource(owner, amount)) return false;

        add(owner, -amount);

        return true;
    }

    /// Moves the value by `amount`, bounded by [#clamp].
    ///
    /// @return the delta actually applied, which differs from `amount` when the value was clamped
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
