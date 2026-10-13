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

    private QuaternionUtils() {}
}
