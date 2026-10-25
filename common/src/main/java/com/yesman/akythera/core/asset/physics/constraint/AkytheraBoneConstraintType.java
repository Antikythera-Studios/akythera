package com.yesman.akythera.core.asset.physics.constraint;

import com.mojang.serialization.MapCodec;

public enum AkytheraBoneConstraintType implements BoneConstraintType {
    LIMIT_ROTATION(LimitRotationConstraint.CODEC),
    TRANSFORMATION(TransformationConstraint.CODEC);

    final MapCodec<? extends BoneConstraint> codec;
    final int id;

    AkytheraBoneConstraintType(MapCodec<? extends BoneConstraint> codec) {
        this.codec = codec;
        id = BoneConstraintType.ENUM_MANAGER.assign(this);
    }

    @Override
    public MapCodec<? extends BoneConstraint> mapCodec() {
        return codec;
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
