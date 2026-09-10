package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.SphereColliderComponent;
import org.jspecify.annotations.Nullable;

/// A ball centred on the pivot. The pivot's rotation and scale are not read: neither deforms a
/// sphere, so [#radius] is the whole shape.
public final class SphereCollider extends Collider {
    public static final MapCodec<SphereCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Transform.CODEC.fieldOf("transform").forGetter(Collider::getPivotTransform),
                Codec.DOUBLE.fieldOf("radius").forGetter(SphereCollider::getRadius)
            )
            .apply(instance, SphereCollider::new)
    );

    private final double radius;

    public SphereCollider(final Transform pivotTransform, final double radius) {
        super(pivotTransform);

        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public SphereColliderComponent instantiate(final @Nullable ComponentAttachable host) {
        return new SphereColliderComponent(this, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.SPHERE;
    }
}
