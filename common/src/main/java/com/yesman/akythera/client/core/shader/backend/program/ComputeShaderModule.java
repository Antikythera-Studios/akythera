package com.yesman.akythera.client.core.shader.backend.program;

/// One compiled compute stage. Linked into a [ComputeProgram] and released right afterwards.
public interface ComputeShaderModule extends AutoCloseable {
    boolean isCompiled();

    String getInfoLog();

    @Override
    void close();
}
