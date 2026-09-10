package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.component.OrientedBoxColliderComponent;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A cuboid, free to rotate with whatever places it.
public final class OrientedBoxCollider extends Collider {
    public static final MapCodec<OrientedBoxCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Transform.CODEC.fieldOf("transform").forGetter(Collider::getPivotTransform),
                Vec3.CODEC.fieldOf("length").forGetter(OrientedBoxCollider::getLength)
            )
            .apply(instance, OrientedBoxCollider::new)
    );

    /// Full length of the cuboid along each of its own axes, before the pivot's scale
    private final Vec3 length;

    /// Unit local axes, +x, +y and +z, carrying the pivot's rotation
    private final Vec3[] axes = new Vec3[3];

    /// Half of [#length] along each of [#axes], the pivot's scale folded in
    private final Vec3 halfExtents;

    public OrientedBoxCollider(final Transform pivotTransform, final Vec3 length) {
        super(pivotTransform);

        this.length = length;
        this.halfExtents = length.scale(0.5D).multiply(pivotTransform.sca());
        axes[0] = VectorUtils.rotateByQuaternion(pivotTransform.rot(), MathUtils.XP);
        axes[1] = VectorUtils.rotateByQuaternion(pivotTransform.rot(), MathUtils.YP);
        axes[2] = VectorUtils.rotateByQuaternion(pivotTransform.rot(), MathUtils.ZP);
    }

    public Vec3 getLength() {
        return length;
    }

    public Vec3 getAxis(final int index) {
        return axes[index];
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    @Override
    public OrientedBoxColliderComponent instantiate(final @Nullable ComponentAttachable host) {
        return new OrientedBoxColliderComponent(this, Transform.identity());
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.ORIENTED_BOX;
    }
}
