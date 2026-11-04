package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

public interface IKSolverType extends EnumerableDataBlock {
    EnumerableDataBlockManager<IKSolverType> ENUM_MANAGER = new EnumerableDataBlockManager<>("ik_solver_type");

    MapCodec<? extends IKSolver> mapCodec();

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
