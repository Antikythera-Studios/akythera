package com.yesman.akythera.core.util.helper;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.util.Util;
import org.joml.Math;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;

public final class QuaternionUtils {
    /// A quaternion as its four components, `[x, y, z, w]`.
    public static final Codec<Quaterniond> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
        list -> Util.fixedSize(list, 4).map(components -> new Quaterniond(components.get(0), components.get(1), components.get(2), components.get(3))),
        quaternion -> List.of(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w())
    );

    /// Transform float quaternion to a double quaternion
    public static Quaterniond floatToDoubleQuaternion(final Quaternionf fQuaternion) {
        return new Quaterniond(fQuaternion.x(), fQuaternion.y(), fQuaternion.z(), fQuaternion.w());
    }

    /// Transform double quaternion to a float quaternion
    public static Quaternionf doubleToFloatQuaternion(final Quaterniond dQuaternion) {
        return new Quaternionf(
            (float)dQuaternion.x(),
            (float)dQuaternion.y(),
            (float)dQuaternion.z(),
            (float)dQuaternion.w()
        );
    }

    @ScriptFunction(pure = true, label = "Make Quaternion", desc = "Makes a quaternion from its four components")
    public static Quaterniond quaternion(final double x, final double y, final double z, final double w) {
        return new Quaterniond(x, y, z, w);
    }

    /// The XYZ Euler angles of `quaternion`, in degrees. The inverse of [#fromEulerDegreesXYZ].
    public static Vector3d toEulerDegreesXYZ(final Quaterniond quaternion) {
        Vector3d radians = quaternion.getEulerAnglesXYZ(new Vector3d());

        return new Vector3d(Math.toDegrees(radians.x), Math.toDegrees(radians.y), Math.toDegrees(radians.z));
    }

    /// The rotation of the XYZ Euler angles `degrees`.
    public static Quaterniond fromEulerDegreesXYZ(final Vector3d degrees) {
        return new Quaterniond().rotationXYZ(Math.toRadians(degrees.x), Math.toRadians(degrees.y), Math.toRadians(degrees.z));
    }

    /// The swing-twist angles of `rotation`, in radians: `y` is the twist around Y, and `x` and `z` are the parts of
    /// the rotation vector of the swing applied after it. The inverse of [#fromSwingTwist].
    public static Vector3d toSwingTwist(final Quaterniond rotation) {
        Quaterniond positive = new Quaterniond(rotation);

        if (positive.w < 0.0D) positive.set(-positive.x, -positive.y, -positive.z, -positive.w);

        double twistLength = Math.sqrt(positive.y * positive.y + positive.w * positive.w);
        Quaterniond twist = twistLength < 1.0E-9D
            ? new Quaterniond()
            : new Quaterniond(0.0D, positive.y / twistLength, 0.0D, positive.w / twistLength);
        Quaterniond swing = positive.mul(twist.conjugate(new Quaterniond()), new Quaterniond());

        if (swing.w < 0.0D) swing.set(-swing.x, -swing.y, -swing.z, -swing.w);

        double swingSine = Math.sqrt(swing.x * swing.x + swing.z * swing.z);
        double swingAngle = 2.0D * Math.atan2(swingSine, swing.w);

        return new Vector3d(
            swingSine < 1.0E-9D ? 0.0D : swing.x / swingSine * swingAngle,
            2.0D * Math.atan2(twist.y, twist.w),
            swingSine < 1.0E-9D ? 0.0D : swing.z / swingSine * swingAngle
        );
    }

    /// The rotation of the swing-twist angles `x`, `y`, `z`, in radians, see [#toSwingTwist].
    public static Quaterniond fromSwingTwist(final double x, final double y, final double z) {
        double swing = Math.sqrt(x * x + z * z);
        Quaterniond rotation = swing < 1.0E-9D ? new Quaterniond() : new Quaterniond().rotationAxis(swing, x / swing, 0.0D, z / swing);

        return rotation.mul(new Quaterniond().rotationY(y));
    }

    private QuaternionUtils() {}
}
