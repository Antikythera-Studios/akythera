package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;

public enum EpicFightColliderType implements ColliderType {
    LINE(LineCollider.CODEC), ORIENTATED_BOUNDING_BOX(OBBCollider.CODEC);

    final MapCodec<? extends Collider> codec;
    final int id;

    EpicFightColliderType(MapCodec<? extends Collider> codec) {
        this.codec = codec;
        this.id = ColliderType.ENUM_MANAGER.assign(this);
    }

    @Override
    public MapCodec<? extends Collider> mapCodec() {
        return this.codec;
    }

    @Override
    public int universalOrdinal() {
        return this.id;
    }
}
