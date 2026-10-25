package com.yesman.akythera.core.asset.physics.constraint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

public interface BoneConstraintType extends EnumerableDataBlock {
    EnumerableDataBlockManager<BoneConstraintType> ENUM_MANAGER = new EnumerableDataBlockManager<>("bone_constraint_type");

    MapCodec<? extends BoneConstraint> mapCodec();

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
