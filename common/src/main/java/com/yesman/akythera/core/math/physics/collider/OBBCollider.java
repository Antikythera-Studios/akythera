package com.yesman.akythera.core.math.physics.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.instance.OBBColliderInstance;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/// Collider in a cuboid shape
///
/// Detects collision by separate axis theorem
public final class OBBCollider extends Collider {
    public static final MapCodec<OBBCollider> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Transform.CODEC.fieldOf("transform").forGetter(Collider::getPivotTransform),
                Codec.DOUBLE.listOf()
                    .comapFlatMap(
                        list -> Util.fixedSize(list, 3)
                            .map(floatList ->
                                new Vec3(
                                    floatList.get(0),
                                    floatList.get(1),
                                    floatList.get(2)
                                )
                            ),
                        vec3 -> List.of(vec3.x(), vec3.y(), vec3.z())
                    )
                    .fieldOf("length").forGetter(OBBCollider::getLength),
                Codec.INT.fieldOf("interpolation").forGetter(Collider::getInterpolations)
            )
            .apply(instance, OBBCollider::new)
    );

    /// Vectors from center of the collider to each vertex in upward plane
    /// Negate the vectors to get downward vertices
    private final Vec3[] upwardVertices = new Vec3[4];

    /// Normals in positive x, y, and z axes in local
    /// Negate the vectors to get -x, -y and -z axes normals in local
    private final Vec3[] normals = new Vec3[3];

    /// Length of the cuboid in each dimension
    private final Vec3 length;

    public OBBCollider(Transform transform, Vec3 length, int interpolations) {
        super(transform, interpolations);

        this.upwardVertices[0] = transform.mulDirection(new Vec3(length.x * 0.5D, length.y * 0.5D, -length.z * 0.5D));
        this.upwardVertices[1] = transform.mulDirection(new Vec3(length.x * 0.5D, length.y * 0.5D, length.z * 0.5D));
        this.upwardVertices[2] = transform.mulDirection(new Vec3(-length.x * 0.5D, length.y * 0.5D, length.z * 0.5D));
        this.upwardVertices[3] = transform.mulDirection(new Vec3(-length.x * 0.5D, length.y * 0.5D, -length.z * 0.5D));
        this.normals[0] = VectorUtils.rotateByQuaternion(transform.rot(), 1.0D, 0.0D, 0.0D);
        this.normals[1] = VectorUtils.rotateByQuaternion(transform.rot(), 0.0D, 1.0D, 0.0D);
        this.normals[2] = VectorUtils.rotateByQuaternion(transform.rot(), 0.0D, 0.0D, 1.0D);
        this.length = length;
    }

    public Vec3 getUpwardVertex(int index) {
        return this.upwardVertices[index];
    }

    public Vec3 getNormal(int index) {
        return this.normals[index];
    }

    public Vec3 getLength() {
        return this.length;
    }

    @Override
    public OBBColliderInstance createInstance(Transform transform) {
        return new OBBColliderInstance(this, transform);
    }

    @Override
    public ColliderType type() {
        return EpicFightColliderType.ORIENTATED_BOUNDING_BOX;
    }
}