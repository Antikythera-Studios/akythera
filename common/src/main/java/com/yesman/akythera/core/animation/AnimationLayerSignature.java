package com.yesman.akythera.core.animation;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

@ScriptType(autoRegister = false)
public interface AnimationLayerSignature extends EnumerableDataBlock {
    EnumerableDataBlockManager<AnimationLayerSignature> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "anim_layer_signature",
        MapCodec.unit(() -> new AnimationLayerSignature() {
            @Override
            public int universalOrdinal() {
                return ENUM_MANAGER.derefOrdinal(this);
            }

            @Override
            public String toString() {
                Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                return enumName == null ? "AnimationLayerSignature" : enumName.toString();
            }
        }
    ));

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
