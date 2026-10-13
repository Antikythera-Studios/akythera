package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.ComputeShaderLoader;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/// Orders the triangles of a skinned output back to front by the distance of their centroids from the camera, the
/// order vanilla gives a translucent render type's quads on upload. A bitonic sort on the GPU, see
/// `triangle_depth_sort.comp`.
public final class TriangleDepthSort implements AutoCloseable {
    private static final Identifier PROGRAM_ID = Akythera.identifier("shaders/compute/triangle_depth_sort.comp");

    /// Invocations per work group, matching `layout(local_size_x)` in the compute source.
    private static final int WORK_GROUP_SIZE = 512;

    /// Entries one work group sorts in shared memory, two per invocation.
    private static final int BLOCK_SIZE = WORK_GROUP_SIZE * 2;

    /// Bytes per sort entry: the key (float) and the triangle index (uint).
    private static final int ENTRY_BYTES = 8;

    // Passes, matching `stage` in the compute source
    private static final int STAGE_KEYS = 0;
    private static final int STAGE_LOCAL_SORT = 1;
    private static final int STAGE_GLOBAL_STEP = 2;
    private static final int STAGE_LOCAL_MERGE = 3;
    private static final int STAGE_INDICES = 4;

    private static @Nullable ComputeProgram program;

    private final int triangleCount;

    /// The sort's entry count: a power of two, at least [#BLOCK_SIZE] and [#triangleCount].
    private final int entryCount;

    private final GpuBuffer entries;

    /// Three indices per triangle, in sorted order.
    private final GpuBuffer indices;

    public TriangleDepthSort(final int vertexCount) {
        triangleCount = vertexCount / 3;
        entryCount = Math.max(BLOCK_SIZE, Integer.highestOneBit(Math.max(1, triangleCount - 1)) << 1);
        entries = ComputeBackends.get().createComputeStorageBuffer(() -> "akythera:triangle_sort_entries", (long)entryCount * ENTRY_BYTES);
        indices = ComputeBackends.get().createIndexStorageBuffer(() -> "akythera:triangle_sort_indices", (long)triangleCount * 3 * Integer.BYTES);
    }

    /// Drops the shared program so the next sort recompiles it from the reloaded pack.
    public static void invalidateProgram() {
        if (program != null) {
            program.close();
            program = null;
        }
    }

    /// Sorts the triangles of `skinnedVertices` and returns the index buffer that draws them farthest from `eye` first.
    /// Its indices are `int`s, [#indexCount] of them.
    ///
    /// @param eye the camera, in the space of the skinned vertices: the origin for vertices placed relative to it
    public GpuBuffer sort(final GpuBuffer skinnedVertices, final Vector3fc eye) {
        dispatch(skinnedVertices, eye, STAGE_KEYS, 0, 0, entryCount / WORK_GROUP_SIZE);
        dispatch(skinnedVertices, eye, STAGE_LOCAL_SORT, 0, 0, entryCount / BLOCK_SIZE);

        for (int size = BLOCK_SIZE * 2; size <= entryCount; size <<= 1) {
            for (int stride = size >> 1; stride >= BLOCK_SIZE; stride >>= 1) {
                dispatch(skinnedVertices, eye, STAGE_GLOBAL_STEP, size, stride, entryCount / BLOCK_SIZE);
            }

            dispatch(skinnedVertices, eye, STAGE_LOCAL_MERGE, size, 0, entryCount / BLOCK_SIZE);
        }

        dispatch(skinnedVertices, eye, STAGE_INDICES, 0, 0, (triangleCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE);

        return indices;
    }

    public int indexCount() {
        return triangleCount * 3;
    }

    /// One pass, ordered against the next by the program's barriers.
    private void dispatch(final GpuBuffer skinnedVertices, final Vector3fc eye, final int stage, final int size, final int stride, final int workGroups) {
        ComputeProgram shader = program();

        shader.uniform("stage").putUnsignedInt(stage);
        shader.uniform("k").putUnsignedInt(size);
        shader.uniform("j").putUnsignedInt(stride);
        shader.uniform("triangleCount").putUnsignedInt(triangleCount);
        shader.uniform("eye").putVector3f(eye);

        try (ComputeProgram.Dispatch dispatch = shader.begin()) {
            dispatch.bindStorageBuffer(0, skinnedVertices)
                .bindStorageBuffer(1, entries)
                .bindStorageBuffer(2, indices)
                .dispatch(workGroups, 1, 1);
        }
    }

    private static ComputeProgram program() {
        if (program == null) {
            program = ComputeShaderLoader.loadComputeShaderProgram(
                ComponentsFactory.getImpl().getResourceManager(),
                PROGRAM_ID,
                BarrierFlags.SHADER_STORAGE,
                BarrierFlags.ELEMENT_ARRAY
            );
        }

        return program;
    }

    @Override
    public void close() {
        entries.close();
        indices.close();
    }
}
