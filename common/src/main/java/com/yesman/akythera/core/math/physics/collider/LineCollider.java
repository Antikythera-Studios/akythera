package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.LineColliderComponent;

public final class LineCollider extends Collider {
    public static final MapCodec<LineCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Transform.CODEC.fieldOf("transform").forGetter(Collider::getPivotTransform),
                Codec.INT.fieldOf("interpolation").forGetter(Collider::getInterpolations)
            )
            .apply(instance, LineCollider::new)
    );

    public LineCollider(Transform pivotTransform, int interpolations) {
        super(pivotTransform, interpolations);
    }

    @Override
    public LineColliderComponent instantiate(final ComponentAttachable host) {
        return new LineColliderComponent(this, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.LINE;
    }
}