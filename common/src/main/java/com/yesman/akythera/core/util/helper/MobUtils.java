package com.yesman.akythera.core.util.helper;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;

/// Replaces the controls a [Mob] moves and turns its body with.
public final class MobUtils {
    /// Makes `mob` move with `moveControl`.
    public static void setMoveControl(final Mob mob, final MoveControl<?> moveControl) {
        mob.moveControl = moveControl;
    }

    /// Makes `mob` turn its body with `bodyRotationControl`.
    public static void setBodyRotationControl(final Mob mob, final BodyRotationControl bodyRotationControl) {
        mob.bodyRotationControl = bodyRotationControl;
    }

    private MobUtils() {}
}
