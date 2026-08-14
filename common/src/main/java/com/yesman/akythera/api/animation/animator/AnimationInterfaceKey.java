package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

/// A key of [AbstractAnimationInterface]
///
/// Although the [AbstractAnimationInterface] client side only code, the keys exist in common side
/// to be included by data packs.
public interface AnimationInterfaceKey extends FSMState {
    EnumerableDataBlockManager<AnimationInterfaceKey> ENUM_MANAGER = new EnumerableDataBlockManager<>("anim_interface_signature");

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
