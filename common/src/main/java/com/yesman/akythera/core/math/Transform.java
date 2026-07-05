package com.yesman.akythera.core.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;

import java.util.List;
import java.util.Optional;

/// Represents a transform in 3-dimensional space into 3 components, location, rotation, and sca
public record Transform(
    Vec3 loc,
    Quaterniond rot,
    Vec3 sca
) {
    public static final Codec<Transform> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Vec3.CODEC.optionalFieldOf("location").forGetter(transform -> Optional.of(transform.loc())),
                Codec.DOUBLE.listOf()
                    .comapFlatMap(
                        list -> Util.fixedSize(list, 4)
                            .map(floatList ->
                                new Quaterniond(
                                    floatList.get(0),
                                    floatList.get(1),
                                    floatList.get(2),
                                    floatList.get(3)
                                )
                            ),
                        quaternion ->
                            List.of(
                                quaternion.x(),
                                quaternion.y(),
                                quaternion.z(),
                                quaternion.w()
                            )
                    )
                    .optionalFieldOf("rotation").forGetter(transform -> Optional.of(transform.rot())),
                Vec3.CODEC.optionalFieldOf("sca").forGetter(transform -> Optional.of(transform.sca()))
            )
        .apply(
            instance,
            (loc, rot, sca) -> new Transform(
                loc.orElse(new Vec3(0.0D, 0.0D, 0.0D)),
                rot.orElse(new Quaterniond()),
                sca.orElse(new Vec3(1.0D, 1.0D, 1.0D))
            )
        )
    );

    /// Readonly immutable zero transform
    public static final Transform ZERO = zero();

    /// Readonly immutable identity transform
    public static final Transform IDENTITY = identity();

    /// Returns an liner-interpolated transform between two
    public static Transform lerp(final Transform from, final Transform to, final float delta) {
        return new Transform(
            new Vec3(
                Mth.lerp(delta, from.loc().x(), to.loc().x()),
                Mth.lerp(delta, from.loc().y(), to.loc().y()),
                Mth.lerp(delta, from.loc().z(), to.loc().z())
            ),
            from.rot().nlerp(to.rot(), delta, new Quaterniond()),
            new Vec3(
                Mth.lerp(delta, from.sca().x(), to.sca().x()),
                Mth.lerp(delta, from.sca().y(), to.sca().y()),
                Mth.lerp(delta, from.sca().z(), to.sca().z())
            )
        );
    }

    /// Returns a transform imported by 4x4 transformation matrix
    public static Transform fromMatrix(final Matrix4f matrix4f) {
        return new Transform(
            VectorUtils.floatToDoubleVector(
                Matrix4fUtils.extractTranslation(matrix4f)
            ),
            QuaternionUtils.floatToDoubleQuaternion(
                Matrix4fUtils.extractRotator(matrix4f)
            ),
            VectorUtils.floatToDoubleVector(
                Matrix4fUtils.extractScale(matrix4f)
            )
        );
    }

    /// Returns a 4x4 transform matrix of the transform
    public Matrix4f toMatrix() {
        return new Matrix4f().translate(
            VectorUtils.doubleToFloatVector(loc)
        ).rotate(
            QuaternionUtils.doubleToFlaotQuaternion(rot)
        ).scale(
            VectorUtils.doubleToFloatVector(sca)
        );
    }

    /// Creates transform by translation
    public static Transform fromTranslation(final Vec3 translation) {
        return new Transform(translation, new Quaterniond(), new Vec3(1.0D, 1.0D, 1.0D));
    }

    /// Creates transform by rotation
    public static Transform fromRotation(final Quaterniond rotation) {
        return new Transform(new Vec3(0.0D, 0.0D, 0.0D), rotation, new Vec3(1.0D, 1.0D, 1.0D));
    }

    /// Creates transform by sca
    public static Transform fromScale(final Vec3 scale) {
        return new Transform(new Vec3(0.0D, 0.0D, 0.0D), new Quaterniond(), scale);
    }

    /// Creates an identity transform
    public static Transform identity() {
        return new Transform(new Vec3(0.0D, 0.0D, 0.0D), new Quaterniond(), new Vec3(1.0D, 1.0D, 1.0D));
    }

    /// Creates a zero transform
    public static Transform zero() {
        return new Transform(new Vec3(0.0D, 0.0D, 0.0D), new Quaterniond(0.0D, 0.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.0D));
    }

    /// Apply translation
    public Transform translate(final Vec3 translation) {
        return new Transform(loc().add(translation), rot(), sca());
    }

    /// Apply rotation
    public Transform rotate(final Quaterniond quaterniond) {
        return new Transform(loc(), rot().mul(quaterniond), sca());
    }

    /// Apply scaling
    public Transform scale(final Vec3 scale) {
        return new Transform(loc(), rot(), sca().multiply(scale));
    }

    /// Multiplies to another transform
    public Transform mul(final Transform operand) {
        return new Transform(
            loc().add(operand.loc()),
            rot().mul(operand.rot()),
            sca().multiply(operand.sca())
        );
    }

    /// Multiplies to a vector
    public Vec3 mul(Vec3 vector) {
        return VectorUtils.rotateByQuaternion(rot(), vector.multiply(sca())).add(loc());
    }

    /// Multiplies to a direction vector, without applying the translation
    public Vec3 mulDirection(Vec3 vector) {
        return VectorUtils.rotateByQuaternion(rot(), vector.multiply(sca()));
    }

    /// Applies the weight
    /// Used for blending poses
    public Transform applyWeight(final float weight) {
        return new Transform(
            loc().scale(weight),
            new Quaterniond(rot().x() * weight, rot().y() * weight, rot().z() * weight, rot().w() * weight),
            sca().scale(weight)
        );
    }

    /// Linearly adds transform value
    /// Used for blending poses
    public Transform add(final Transform opponent) {
        return new Transform(
            loc().add(opponent.loc()),
            rot().add(opponent.rot()),
            sca().add(opponent.sca())
        );
    }

}
