package com.yesman.akythera.core.util.helper;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

public final class Matrix4fUtils {
    /// Extract translation from matrix
    public static Vector3f extractTranslation(final Matrix4f matrix4f) {
        return new Vector3f(matrix4f.m30(), matrix4f.m31(), matrix4f.m32());
    }

    /// Extract rotator in a quaternion from matrix
    public static Quaternionf extractRotator(final Matrix4f matrix4f) {
        return matrix4f.getUnnormalizedRotation(new Quaternionf());
    }

    /// Extract sca from matrix
    public static Vector3f extractScale(final Matrix4f matrix4f) {
        Vector3f scaleX = new Vector3f(matrix4f.m00(), matrix4f.m01(), matrix4f.m02());
        Vector3f scaleY = new Vector3f(matrix4f.m10(), matrix4f.m11(), matrix4f.m12());
        Vector3f scaleZ = new Vector3f(matrix4f.m20(), matrix4f.m21(), matrix4f.m22());

        return new Vector3f(scaleX.length(), scaleY.length(), scaleZ.length());
    }

    public static Matrix4f[] allocateMatrixArray(final int count) {
        Matrix4f[] matrices = new Matrix4f[count];

        for (int i = 0; i < count; i++) {
            matrices[i] = new Matrix4f();
        }

        return matrices;
    }

    /// Push matrix elements into buffer
    public static void uploadBuffer(final Matrix4f mat, final FloatBuffer buf) {
        buf.put(mat.m00());
        buf.put(mat.m01());
        buf.put(mat.m02());
        buf.put(mat.m03());
        buf.put(mat.m10());
        buf.put(mat.m11());
        buf.put(mat.m12());
        buf.put(mat.m13());
        buf.put(mat.m20());
        buf.put(mat.m21());
        buf.put(mat.m22());
        buf.put(mat.m23());
        buf.put(mat.m30());
        buf.put(mat.m31());
        buf.put(mat.m32());
        buf.put(mat.m33());
    }

    private Matrix4fUtils() {}
}
