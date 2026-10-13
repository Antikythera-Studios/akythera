package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.CapsuleColliderComponent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A segment swept by a ball. [#height] is the distance between the two hemisphere centres, laid
/// along the pivot's local +Y and centred on the pivot. Height 0 makes a sphere, radius 0 a line.
public final class CapsuleCollider extends Collider {
    public static final MapCodec<CapsuleCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                TRANSFORM_CODEC.forGetter(Collider::getPivotTransform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(Collider::getTransformInheritPolicy),
                Codec.DOUBLE.fieldOf("radius").forGetter(CapsuleCollider::getRadius),
                Codec.DOUBLE.fieldOf("height").forGetter(CapsuleCollider::getHeight),
                ENABLED_CODEC.forGetter(Collider::enabled)
            )
            .apply(instance, CapsuleCollider::new)
    );

    private final double radius;
    private final double height;

    /// The two hemisphere centres in the collider's own space, the pivot's rotation and scale folded in
    private final Vec3 localEndA;
    private final Vec3 localEndB;

    public CapsuleCollider(
        final Transform pivotTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final double radius,
        final double height,
        final boolean enabled
    ) {
        super(pivotTransform, transformInheritPolicy, enabled);

        this.radius = radius;
        this.height = height;

        Vec3 halfAxis = pivotTransform.mulDirection(new Vec3(0.0D, height * 0.5D, 0.0D));
        this.localEndA = pivotTransform.loc().subtract(halfAxis);
        this.localEndB = pivotTransform.loc().add(halfAxis);
    }

    public double getRadius() {
        return radius;
    }

    public double getHeight() {
        return height;
    }

    public Vec3 getLocalEndA() {
        return localEndA;
    }

    public Vec3 getLocalEndB() {
        return localEndB;
    }

    @Override
    public CapsuleColliderComponent instantiate(final @Nullable ComponentAttachable host) {
        return new CapsuleColliderComponent(this, host, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.CAPSULE;
    }
}
