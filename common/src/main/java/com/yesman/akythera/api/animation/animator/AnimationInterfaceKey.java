package com.yesman.akythera.api.animation.animator;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

/// A key of [AbstractAnimationInterface]
///
/// Although the [AbstractAnimationInterface] client side only code, the keys exist in common side
/// to be included by data packs.
public interface AnimationInterfaceKey extends FSMState {
    EnumerableDataBlockManager<AnimationInterfaceKey> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "anim_interface_signature",
        MapCodec.unit(() -> new AnimationInterfaceKey() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                return enumName == null ? "AnimationInterfaceKey" : enumName.toString();
            }
        }
    ));

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
