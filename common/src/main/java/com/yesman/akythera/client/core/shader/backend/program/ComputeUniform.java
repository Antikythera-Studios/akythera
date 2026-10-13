package com.yesman.akythera.client.core.shader.backend.program;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/// One uniform slot of a [ComputeProgram]. A name the program does not declare resolves to a handle
/// whose writes are dropped.
public interface ComputeUniform {
    void putMatrix3f(Matrix3f value);

    void putMatrix4f(Matrix4f value);

    void putUnsignedInt(int value);

    void putVector3f(Vector3fc value);
}
