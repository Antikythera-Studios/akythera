package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.SphereColliderComponent;
import org.jspecify.annotations.Nullable;

/// A ball centred on the pivot. The pivot's rotation and scale are not read: neither deforms a
/// sphere, so [#radius] is the whole shape until an inherited scale grows it
/// ([TransformInheritPolicy#inheritsScale]).
public final class SphereCollider extends Collider {
    public static final MapCodec<SphereCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                TRANSFORM_CODEC.forGetter(Collider::getPivotTransform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(Collider::getTransformInheritPolicy),
                Codec.DOUBLE.fieldOf("radius").forGetter(SphereCollider::getRadius),
                ENABLED_CODEC.forGetter(Collider::enabled)
            )
            .apply(instance, SphereCollider::new)
    );

    private final double radius;

    public SphereCollider(
        final Transform pivotTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final double radius,
        final boolean enabled
    ) {
        super(pivotTransform, transformInheritPolicy, enabled);

        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public SphereColliderComponent instantiate(final @Nullable ComponentAttachable host) {
        return new SphereColliderComponent(this, host, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.SPHERE;
    }
}
