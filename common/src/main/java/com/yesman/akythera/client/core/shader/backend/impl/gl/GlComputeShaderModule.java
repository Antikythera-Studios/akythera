package com.yesman.akythera.client.core.shader.backend.impl.gl;

import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;
import org.lwjgl.opengl.ARBComputeShader;

import static org.lwjgl.opengl.GL20C.*;

public final class GlComputeShaderModule implements ComputeShaderModule {
    final int handle;

    GlComputeShaderModule(final String source) {
        handle = glCreateShader(ARBComputeShader.GL_COMPUTE_SHADER);
        glShaderSource(handle, source);
        glCompileShader(handle);
    }

    @Override
    public boolean isCompiled() {
        return glGetShaderi(handle, GL_COMPILE_STATUS) == GL_TRUE;
    }

    @Override
    public String getInfoLog() {
        return glGetShaderInfoLog(handle);
    }

    @Override
    public void close() {
        glDeleteShader(handle);
    }
}