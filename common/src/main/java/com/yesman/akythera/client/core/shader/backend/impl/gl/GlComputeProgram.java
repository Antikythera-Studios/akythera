package com.yesman.akythera.client.core.shader.backend.impl.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeUniform;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.opengl.ARBComputeShader;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL30C.*;

public final class GlComputeProgram implements ComputeProgram {
    private final int handle;
    private final int barrierBits;
    private final Map<String, ComputeUniform> uniforms = new HashMap<>();
    private final GlDispatch dispatch = new GlDispatch();

    GlComputeProgram(final int handle, final int barrierBits) {
        this.handle = handle;
        this.barrierBits = barrierBits;
    }

    @Override
    public ComputeProgram.Dispatch begin() {
        return dispatch.open();
    }

    @Override
    public ComputeUniform uniform(final String name) {
        return uniforms.computeIfAbsent(name, key -> new GlComputeUniform(handle, glGetUniformLocation(handle, key)));
    }

    @Override
    public boolean isLinked() {
        return glGetProgrami(handle, GL_LINK_STATUS) == GL_TRUE;
    }

    @Override
    public String getInfoLog() {
        return glGetProgramInfoLog(handle);
    }

    @Override
    public void close() {
        glDeleteProgram(handle);
    }

    /// Reused across dispatches: only one dispatch is ever open, on the render thread.
    private final class GlDispatch implements ComputeProgram.Dispatch {
        private final IntArrayList takenBindings = new IntArrayList();

        /// The graphics program bound when this scope opened, restored when it closes.
        ///
        /// `GlCommandEncoder` caches the program it last bound in its `lastProgram` field and never
        /// invalidates it — `lastPipeline` is cleared whenever a render pass is created, but
        /// `lastProgram` is only ever assigned. `GlStateManager._glUseProgram` is a bare pass-through
        /// with no cache of its own. So a plain `glUseProgram` here would leave the encoder believing
        /// its program is still current, and the next vanilla draw that happens to reuse the same
        /// `GlProgram` would skip its rebind and render with this compute program bound. Reading the
        /// current program back and putting it in place on close leaves that cache truthful.
        private int previousProgram;

        GlDispatch open() {
            previousProgram = GlStateManager._getInteger(GL_CURRENT_PROGRAM);
            GlStateManager._glUseProgram(handle);

            return this;
        }

        @Override
        public ComputeProgram.Dispatch bindStorageBuffer(final int binding, final GpuBuffer buffer) {
            glBindBufferBase(
                ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER,
                binding,
                ((GlBuffer) buffer).handle
            );
            takenBindings.add(binding);

            return this;
        }

        @Override
        public ComputeProgram.Dispatch bindStorageBuffer(final int binding, final GpuBufferSlice slice) {
            glBindBufferRange(
                ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER,
                binding,
                ((GlBuffer)slice.buffer()).handle,
                slice.offset(),
                slice.length()
            );
            takenBindings.add(binding);

            return this;
        }

        @Override
        public void dispatch(final int countX, final int countY, final int countZ) {
            ARBComputeShader.glDispatchCompute(countX, countY, countZ);
            ARBShaderImageLoadStore.glMemoryBarrier(barrierBits);
        }

        @Override
        public void close() {
            for (int i = 0; i < takenBindings.size(); i++) {
                glBindBufferBase(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, takenBindings.getInt(i), 0);
            }

            takenBindings.clear();
            GlStateManager._glUseProgram(previousProgram);
        }
    }
}