package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

/// A state signature of a mob behavior state machine.
public interface MobBehaviorState extends FSMState {
    EnumerableDataBlockManager<MobBehaviorState> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "state_ids/mob_behavior",
        MapCodec.unit(() -> new MobBehaviorState() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                return enumName == null ? "MobBehaviorState" : enumName.toString();
            }
        })
    );

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
