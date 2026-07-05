package com.yesman.akythera.core.util.helper;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Matrix4fUtils {
    /// Extract translation from matrix
    public static Vector3f extractTranslation(Matrix4f matrix4f) {
        return new Vector3f(matrix4f.m30(), matrix4f.m31(), matrix4f.m32());
    }

    /// Extract rotator in a quaternion from matrix
    public static Quaternionf extractRotator(Matrix4f matrix4f) {
        return matrix4f.getUnnormalizedRotation(new Quaternionf());
    }

    /// Extract sca from matrix
    public static Vector3f extractScale(Matrix4f matrix4f) {
        Vector3f scaleX = new Vector3f(matrix4f.m00(), matrix4f.m01(), matrix4f.m02());
        Vector3f scaleY = new Vector3f(matrix4f.m10(), matrix4f.m11(), matrix4f.m12());
        Vector3f scaleZ = new Vector3f(matrix4f.m20(), matrix4f.m21(), matrix4f.m22());

        return new Vector3f(scaleX.length(), scaleY.length(), scaleZ.length());
    }

    public static Matrix4f[] allocateMatrixArray(int count) {
        Matrix4f[] matrices = new Matrix4f[count];

        for (int i = 0; i < count; i++) {
            matrices[i] = new Matrix4f();
        }

        return matrices;
    }

    private Matrix4fUtils() {}
}
