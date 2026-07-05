package com.yesman.akythera.core.util.helper;

import org.joml.Quaterniond;
import org.joml.Quaternionf;

public final class QuaternionUtils {
    /// Transform float quaternion to a double quaternion
    public static Quaterniond floatToDoubleQuaternion(Quaternionf fQuaternion) {
        return new Quaterniond(fQuaternion.x(), fQuaternion.y(), fQuaternion.z(), fQuaternion.w());
    }

    /// Transform double quaternion to a float quaternion
    public static Quaternionf doubleToFlaotQuaternion(Quaterniond dQuaternion) {
        return new Quaternionf(
            (float)dQuaternion.x(),
            (float)dQuaternion.y(),
            (float)dQuaternion.z(),
            (float)dQuaternion.w()
        );
    }

    private QuaternionUtils() {}
}
