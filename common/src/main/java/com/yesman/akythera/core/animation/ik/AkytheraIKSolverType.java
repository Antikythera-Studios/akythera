package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.MapCodec;

public enum AkytheraIKSolverType implements IKSolverType {
    TWO_BONE(TwoBoneIKSolver.CODEC),
    FABRIK(FabrikIKSolver.CODEC);

    final MapCodec<? extends IKSolver> codec;
    final int id;

    AkytheraIKSolverType(final MapCodec<? extends IKSolver> codec) {
        this.codec = codec;
        id = IKSolverType.ENUM_MANAGER.assign(this);
    }

    @Override
    public MapCodec<? extends IKSolver> mapCodec() {
        return codec;
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
