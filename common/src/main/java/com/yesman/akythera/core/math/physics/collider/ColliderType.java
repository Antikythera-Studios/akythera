package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

public interface ColliderType extends EnumerableDataBlock {
    EnumerableDataBlockManager<ColliderType> ENUM_MANAGER = new EnumerableDataBlockManager<>("collider_type");

    MapCodec<? extends Collider> mapCodec();

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
