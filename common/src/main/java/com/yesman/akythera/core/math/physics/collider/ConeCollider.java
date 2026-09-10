package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.ConeColliderComponent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A right circular cone laid along the pivot's local +Y and centred on the pivot: the base disc sits
/// at `-height / 2`, the apex at `+height / 2`.
public final class ConeCollider extends Collider {
    public static final MapCodec<ConeCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Transform.CODEC.fieldOf("transform").forGetter(Collider::getPivotTransform),
                Codec.DOUBLE.fieldOf("radius").forGetter(ConeCollider::getRadius),
                Codec.DOUBLE.fieldOf("height").forGetter(ConeCollider::getHeight)
            )
            .apply(instance, ConeCollider::new)
    );

    private final double radius;
    private final double height;

    /// Apex and base centre in the collider's own space, the pivot's rotation and scale folded in
    private final Vec3 localApex;
    private final Vec3 localBaseCenter;

    public ConeCollider(final Transform pivotTransform, final double radius, final double height) {
        super(pivotTransform);

        this.radius = radius;
        this.height = height;

        Vec3 halfAxis = pivotTransform.mulDirection(new Vec3(0.0D, height * 0.5D, 0.0D));
        this.localApex = pivotTransform.loc().add(halfAxis);
        this.localBaseCenter = pivotTransform.loc().subtract(halfAxis);
    }

    public double getRadius() {
        return radius;
    }

    public double getHeight() {
        return height;
    }

    public Vec3 getLocalApex() {
        return localApex;
    }

    public Vec3 getLocalBaseCenter() {
        return localBaseCenter;
    }

    @Override
    public ConeColliderComponent instantiate(final @Nullable ComponentAttachable host) {
        return new ConeColliderComponent(this, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.CONE;
    }
}
