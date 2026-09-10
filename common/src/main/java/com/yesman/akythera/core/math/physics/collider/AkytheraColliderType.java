package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;

public enum AkytheraColliderType implements ColliderType {
    ORIENTED_BOX(OrientedBoxCollider.CODEC),
    SPHERE(SphereCollider.CODEC),
    CONE(ConeCollider.CODEC),
    CAPSULE(CapsuleCollider.CODEC);

    final MapCodec<? extends Collider> codec;
    final int id;

    AkytheraColliderType(MapCodec<? extends Collider> codec) {
        this.codec = codec;
        id = ColliderType.ENUM_MANAGER.assign(this);
    }

    @Override
    public MapCodec<? extends Collider> mapCodec() {
        return codec;
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
