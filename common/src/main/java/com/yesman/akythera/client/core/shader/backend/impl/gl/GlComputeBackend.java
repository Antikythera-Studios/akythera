package com.yesman.akythera.client.core.shader.backend.impl.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.client.core.shader.backend.ComputeBackend;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeShaderModule;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL32C.*;

/// Compute backend over the OpenGL context Minecraft creates, 3.3 core since 26.1. The static
/// import marks that floor: anything the compute path needs from above it is named through the ARB
/// extension that introduced it.
public final class GlComputeBackend implements ComputeBackend {
    /// GLSL 330 is what a 3.3 core profile caps out at, so every construct the compute path takes
    /// from a later level is switched on by name. `AKYTHERA_GL` selects the loose-uniform half of a
    /// shader's uniform declarations, which is all OpenGL accepts.
    private static final String PREAMBLE = """
        #version 330 core
        #extension GL_ARB_compute_shader : require
        #extension GL_ARB_shader_storage_buffer_object : require
        #extension GL_ARB_shader_image_load_store : require
        #extension GL_ARB_shading_language_packing : require
        #define AKYTHERA_GL
        """;

    @Override
    public ComputeShaderModule compile(final String body) {
        return new GlComputeShaderModule(PREAMBLE + body);
    }

    @Override
    public ComputeProgram link(final ComputeShaderModule module, final BarrierFlags... barriers) {
        int handle = glCreateProgram();

        glAttachShader(handle, ((GlComputeShaderModule) module).handle);
        glLinkProgram(handle);

        return new GlComputeProgram(handle, toGlBits(barriers));
    }

    @Override
    public GpuBuffer createStorageBuffer(final Supplier<String> label, final ByteBuffer data) {
        return RenderSystem.getDevice().createBuffer(label, 0, data);
    }

    @Override
    public GpuBuffer createVertexStorageBuffer(final Supplier<String> label, final long size) {
        return RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_VERTEX, size);
    }

    @Override
    public GpuBuffer createMappedStorageBuffer(final Supplier<String> label, final long size) {
        return RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_MAP_WRITE, size);
    }

    @Override
    public int storageBufferOffsetAlignment() {
        return glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
    }

    @Override
    public long maxStorageBlockSize() {
        return glGetInteger64(ARBShaderStorageBufferObject.GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
    }

    private static int toGlBits(final BarrierFlags... barriers) {
        int bits = 0;

        for (BarrierFlags barrier : barriers) {
            bits |= switch (barrier) {
                case SHADER_STORAGE   -> ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BARRIER_BIT;
                case VERTEX_ATTRIBUTE -> ARBShaderImageLoadStore.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT;
                case ELEMENT_ARRAY    -> ARBShaderImageLoadStore.GL_ELEMENT_ARRAY_BARRIER_BIT;
                case ATOMIC_COUNTER   -> ARBShaderImageLoadStore.GL_ATOMIC_COUNTER_BARRIER_BIT;
                case COMMAND          -> ARBShaderImageLoadStore.GL_COMMAND_BARRIER_BIT;
            };
        }

        return bits;
    }
}