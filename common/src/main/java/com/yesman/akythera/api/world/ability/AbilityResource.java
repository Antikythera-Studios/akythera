package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;

public interface AbilityResource {
    /// Checks if the owner has sufficient ability source. Doesn't consume it
    ///
    /// Called normally in client side
    boolean hasResource(final LivingEntityPatch<?> owner, final float amount);

    /// Checks and consumes the ability resource from the owner. If the source
    /// is insufficient, it won't consume the resource.
    ///
    /// Called normally in client side
    boolean consume(final LivingEntityPatch<?> owner, final float amount);
}
