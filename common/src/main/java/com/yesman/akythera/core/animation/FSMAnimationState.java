package com.yesman.akythera.core.animation;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

public interface FSMAnimationState extends FSMState {
    EnumerableDataBlockManager<FSMAnimationState> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "fsm_anim_state",
        MapCodec.unit(() -> new FSMAnimationState() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                return enumName == null ? "FSMAnimationState" : enumName.toString();
            }
        }
    ));

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
