package com.yesman.akythera.core.asset.physics.constraint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/// Keeps the owner's pose rotation inside a range per axis, like Blender's Limit Rotation. A rag doll's solver holds
/// its joint inside the limits.
///
/// The rotation is read as [QuaternionUtils#toSwingTwist]: a twist around the bone's Y axis, the axis it points along,
/// and a swing that tips Y away. The swing is limited by its X and Z parts, which is the area [#x] and [#z] draw on a
/// sphere around the bone's head.
///
/// @param x              swing around the bone's X axis
/// @param y              twist around the bone's Y axis
/// @param z              swing around the bone's Z axis
/// @param simulationOnly whether animated poses may leave the limits
public record LimitRotationConstraint(AngularLimit x, AngularLimit y, AngularLimit z, boolean simulationOnly) implements BoneConstraint {
    public static final LimitRotationConstraint FREE = new LimitRotationConstraint(AngularLimit.FREE, AngularLimit.FREE, AngularLimit.FREE, true);

    public static final MapCodec<LimitRotationConstraint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AngularLimit.CODEC.optionalFieldOf("x", AngularLimit.FREE).forGetter(LimitRotationConstraint::x),
                AngularLimit.CODEC.optionalFieldOf("y", AngularLimit.FREE).forGetter(LimitRotationConstraint::y),
                AngularLimit.CODEC.optionalFieldOf("z", AngularLimit.FREE).forGetter(LimitRotationConstraint::z),
                Codec.BOOL.optionalFieldOf("simulation_only", true).forGetter(LimitRotationConstraint::simulationOnly)
            ).apply(instance, LimitRotationConstraint::new)
    );

    @Override
    public BoneConstraintType type() {
        return AkytheraBoneConstraintType.LIMIT_ROTATION;
    }

    /// The solver holds a rag doll inside the limits, so a simulated pose is left as it is.
    @Override
    public boolean posesWhen(final boolean simulated) {
        return !simulated && !simulationOnly;
    }

    @Override
    public Transform apply(final Transform transform, final Pose pose) {
        Quaterniond limited = new Quaterniond();

        return limit(transform.rot(), limited) ? new Transform(transform.loc(), limited, transform.sca()) : transform;
    }

    /// Whether any of the three limits holds the rotation.
    public boolean limitsRotation() {
        return !x.isFree() || !y.isFree() || !z.isFree();
    }

    /// Writes into `dest` the rotation `local` is limited to. False when `local` is inside the limits already.
    public boolean limit(final Quaterniond local, final Quaterniond dest) {
        Vector3d angles = QuaternionUtils.toSwingTwist(local);
        double limitedX = x.clamp(angles.x);
        double limitedY = y.clamp(angles.y);
        double limitedZ = z.clamp(angles.z);

        if (limitedX == angles.x && limitedY == angles.y && limitedZ == angles.z) return false;

        dest.set(QuaternionUtils.fromSwingTwist(limitedX, limitedY, limitedZ));

        return true;
    }

    /// A range of angles, in degrees within [-180, 180].
    public record AngularLimit(float min, float max) {
        public static final AngularLimit FREE = new AngularLimit(-180.0F, 180.0F);

        public static final Codec<AngularLimit> CODEC = RecordCodecBuilder.<AngularLimit>create(
            instance ->
                instance.group(
                    Codec.floatRange(-180.0F, 180.0F).fieldOf("min").forGetter(AngularLimit::min),
                    Codec.floatRange(-180.0F, 180.0F).fieldOf("max").forGetter(AngularLimit::max)
                ).apply(instance, AngularLimit::new)
        ).validate(limit -> limit.min() <= limit.max()
            ? DataResult.success(limit)
            : DataResult.error(() -> "Angular limit min " + limit.min() + " exceeds max " + limit.max()));

        public boolean isFree() {
            return min <= -180.0F && max >= 180.0F;
        }

        /// `radians` held inside this range.
        public double clamp(final double radians) {
            return isFree() ? radians : Math.clamp(radians, Math.toRadians(min), Math.toRadians(max));
        }
    }
}
