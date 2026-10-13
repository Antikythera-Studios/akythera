package com.yesman.akythera.client.core.shader.backend.program;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/// A linked compute program.
///
/// Blaze3D models no compute stage: `com.mojang.blaze3d.shaders.ShaderType` is VERTEX and FRAGMENT
/// only, `com.mojang.blaze3d.systems.CommandEncoder` exposes no dispatch, and [GpuBuffer]'s usage
/// flags stop short of a storage-buffer bit. This interface is the one seam where Akythera leaves
/// the sanctioned graphics API; [com.yesman.akythera.client.core.shader.backend.ComputeBackends]
/// selects the implementation.
public interface ComputeProgram extends AutoCloseable {
    /// Makes this program current and opens the scope that records one dispatch.
    Dispatch begin();

    ComputeUniform uniform(String name);

    boolean isLinked();

    String getInfoLog();

    @Override
    void close();

    /// The program-current scope. Closing it drops the storage buffer bindings it took and hands the
    /// previously bound graphics program back.
    interface Dispatch extends AutoCloseable {
        Dispatch bindStorageBuffer(int binding, GpuBuffer buffer);

        Dispatch bindStorageBuffer(int binding, GpuBufferSlice slice);

        /// Runs `countX * countY * countZ` work groups, then raises the barriers the program was linked with.
        void dispatch(int countX, int countY, int countZ);

        @Override
        void close();
    }
}
