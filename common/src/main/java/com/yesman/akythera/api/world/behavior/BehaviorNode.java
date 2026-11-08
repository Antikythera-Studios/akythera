package com.yesman.akythera.api.world.behavior;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/// A node of a behavior tree built for one brain by its [BehaviorNodeBlueprint]. Holds its parameters and its
/// runtime state as fields.
public interface BehaviorNode {
    /// Whether the node also runs on the ticks between two full brain ticks, like [Goal#requiresUpdateEveryTick].
    default boolean requiresUpdateEveryTick() {
        return false;
    }

    /// `ticks` counted in the ticks this node runs on, like [Goal#adjustedTickDelay].
    default int adjustedTickDelay(final int ticks) {
        return requiresUpdateEveryTick() ? ticks : Mth.positiveCeilDiv(ticks, 2);
    }
}
