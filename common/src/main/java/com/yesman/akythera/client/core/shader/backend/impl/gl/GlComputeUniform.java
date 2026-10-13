package com.yesman.akythera.client.core.shader.backend.impl.gl;

import com.yesman.akythera.client.core.shader.backend.program.ComputeUniform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import org.lwjgl.opengl.ARBSeparateShaderObjects;
import org.lwjgl.system.MemoryStack;

/// Uploads through `glProgramUniform*`, which addresses the program by name and so needs no
/// program to be current.
record GlComputeUniform(int programHandle, int location) implements ComputeUniform {
    @Override
    public void putMatrix3f(final Matrix3f value) {
        if (location < 0) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ARBSeparateShaderObjects.glProgramUniformMatrix3fv(programHandle, location, false, value.get(stack.callocFloat(9)));
        }
    }

    @Override
    public void putMatrix4f(final Matrix4f value) {
        if (location < 0) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ARBSeparateShaderObjects.glProgramUniformMatrix4fv(programHandle, location, false, value.get(stack.callocFloat(16)));
        }
    }

    @Override
    public void putUnsignedInt(final int value) {
        if (location < 0) return;

        ARBSeparateShaderObjects.glProgramUniform1ui(programHandle, location, value);
    }

    @Override
    public void putVector3f(final Vector3fc value) {
        if (location < 0) return;

        ARBSeparateShaderObjects.glProgramUniform3f(programHandle, location, value.x(), value.y(), value.z());
    }
}