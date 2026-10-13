package com.yesman.akythera.core.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;

import java.util.Optional;

/// Represents a transform in 3-dimensional space into 3 components, location, rotation, and sca
@ScriptType(autoRegister = false)
public record Transform(
    Vec3 loc,
    Quaterniond rot,
    Vec3 sca
) {
    public static final Codec<Transform> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Vec3.CODEC.optionalFieldOf("location").forGetter(transform -> Optional.of(transform.loc())),
                QuaternionUtils.CODEC.optionalFieldOf("rotation").forGetter(transform -> Optional.of(transform.rot())),
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

    /// Returns a transform between two: linear for the location and the scale, spherical for the rotation, which turns
    /// at a constant rate
    public static Transform slerp(final Transform from, final Transform to, final float delta) {
        return new Transform(
            from.loc().lerp(to.loc(), delta),
            from.rot().slerp(to.rot(), delta, new Quaterniond()),
            from.sca().lerp(to.sca(), delta)
        );
    }

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
            QuaternionUtils.doubleToFloatQuaternion(rot)
        ).scale(
            VectorUtils.doubleToFloatVector(sca)
        );
    }

    @ScriptFunction(pure = true, label = "Make Transform", desc = "Makes a transform from a location, a rotation and a scale")
    public static Transform of(final Vec3 location, final Quaterniond rotation, final Vec3 scale) {
        return new Transform(location, rotation, scale);
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
        return new Transform(loc(), rot().mul(quaterniond, new Quaterniond()), sca());
    }

    /// Apply scaling
    public Transform scale(final double scale) {
        return new Transform(loc(), rot(), sca().scale(scale));
    }

    /// Apply scaling
    public Transform scale(final Vec3 scale) {
        return new Transform(loc(), rot(), sca().multiply(scale));
    }

    /// Composes with a child transform: the child is rotated and scaled into this one's space before
    /// its translation is added, so `parent.mul(child)` places `child` under `parent`.
    public Transform mul(final Transform operand) {
        return new Transform(
            loc().add(mulDirection(operand.loc())),
            rot().mul(operand.rot(), new Quaterniond()),
            sca().multiply(operand.sca())
        );
    }

    /// Multiplies to a vector
    public Vec3 mul(final Vec3 vector) {
        return VectorUtils.rotateByQuaternion(rot(), vector.multiply(sca())).add(loc());
    }

    /// Multiplies to a direction vector, without applying the translation
    public Vec3 mulDirection(final Vec3 vector) {
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

    /// This transform expressed relative to `base`, component-wise: translation subtracted,
    /// rotation right-multiplied by the inverse, scale divided.
    ///
    /// @see com.yesman.akythera.core.asset.loader.AnimationLoader.ImportType#ADDITIVE
    public Transform relativeTo(final Transform base) {
        return new Transform(
            loc().subtract(base.loc()),
            rot().mul(base.rot().invert(new Quaterniond()), new Quaterniond()).normalize(),
            new Vec3(
                MathUtils.divideOrZero(sca().x(), base.sca().x()),
                MathUtils.divideOrZero(sca().y(), base.sca().y()),
                MathUtils.divideOrZero(sca().z(), base.sca().z())
            )
        );
    }

    /// This transform with the additive delta `additive` laid on top of it, scaled by `weight`: the
    /// inverse of [#relativeTo], where a weight of 0 yields this transform unchanged and 1 yields the
    /// transform the delta was taken from. The delta is scaled toward its neutral element per
    /// component—a zero translation, the identity rotation and a unit scale.
    ///
    /// @param weight in [0, 1]
    public Transform applyAdditive(final Transform additive, final float weight) {
        return new Transform(
            loc().add(additive.loc().scale(weight)),
            new Quaterniond().slerp(additive.rot(), weight).mul(rot()).normalize(),
            new Vec3(
                sca().x() * Mth.lerp(weight, 1.0D, additive.sca().x()),
                sca().y() * Mth.lerp(weight, 1.0D, additive.sca().y()),
                sca().z() * Mth.lerp(weight, 1.0D, additive.sca().z())
            )
        );
    }
}
