package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.client.core.shader.backend.ComputeBackend;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/// The per-mesh GPU residency of a skinned mesh: the immutable source buffers a skinning dispatch
/// reads and the vertex buffer it writes.
///
/// Owns no VAO and no attribute state. The output is a vertex-readable [GpuBuffer], handed straight
/// to `RenderPass#setVertexBuffer`, and the backend builds the vertex array from the render
/// pipeline's own `VertexFormat`. Which means the output stride a subclass declares is not a free
/// choice: it must match that format byte for byte.
public abstract class AnimationComputeShader implements AutoCloseable {
    // int, float
    public static final int DATA_SIZE_IN_BYTE = 4;

    /// Invocations per work group, matching `layout(local_size_x)` in the compute sources.
    public static final int WORK_GROUP_SIZE = 128;

    /// Bytes per vertex in [#vertices]: position, normal, uv, the bone from–to index pair, and the
    /// index of the mesh part the vertex belongs to.
    private static final int VERTEX_STRIDE = (3 + 3 + 2 + 2 + 1) * DATA_SIZE_IN_BYTE;

    /// Bytes per entry in [#boneWeightMapping]: bone id(int) and weight(float).
    private static final int BONE_WEIGHT_STRIDE = 2 * DATA_SIZE_IN_BYTE;

    /// A buffer stores vertex data. Static since vertex data never changes in runtime.
    private final GpuBuffer vertices;

    /// The (bone id, weight) pairs [#vertices]' from–to pair indexes into.
    private final GpuBuffer boneWeightMapping;

    /// A buffer the skinning dispatch writes its result into, read back as vertex attributes.
    private final GpuBuffer output;

    private final int vertexCount;

    /// Orders [#output]'s triangles for a translucent draw. Created on the first one.
    private @Nullable TriangleDepthSort depthSort;

    /// Part indices written into [#vertices] are the ordinal of the part in
    /// `SkeletalMeshBuilder#partBuilders`' iteration order. `SkeletalMesh`'s constructor walks the
    /// same map into the `ImmutableMap` behind `SkeletalMesh#getParts`, so part index *n* here and
    /// the *n*-th entry of `getParts()` are the same part. Whoever writes the visibility bitset
    /// walks `getParts()` and relies on that.
    protected AnimationComputeShader(final SkeletalMesh.SkeletalMeshBuilder meshBuilder, final int outputVertexStride) {
        int count = 0;

        for (List<VertexBuilder> part : meshBuilder.partBuilders.values()) {
            count += part.size();
        }

        vertexCount = count;

        ByteBuffer vertexData = MemoryUtil.memAlloc(vertexCount * VERTEX_STRIDE);
        ByteBuffer weightMappingData = MemoryUtil.memAlloc(meshBuilder.affectingBoneIdArray.length * BONE_WEIGHT_STRIDE);

        try {
            int partIndex = 0;

            for (List<VertexBuilder> part : meshBuilder.partBuilders.values()) {
                for (VertexBuilder vb : part) {
                    int posIdx = vb.position();
                    int normalIdx = vb.normal();
                    int uvIdx = vb.uv();

                    vertexData.putFloat(meshBuilder.positions[posIdx * 3]);
                    vertexData.putFloat(meshBuilder.positions[posIdx * 3 + 1]);
                    vertexData.putFloat(meshBuilder.positions[posIdx * 3 + 2]);
                    vertexData.putFloat(meshBuilder.normals[normalIdx * 3]);
                    vertexData.putFloat(meshBuilder.normals[normalIdx * 3 + 1]);
                    vertexData.putFloat(meshBuilder.normals[normalIdx * 3 + 2]);
                    vertexData.putFloat(meshBuilder.uvs[uvIdx * 2]);
                    vertexData.putFloat(meshBuilder.uvs[uvIdx * 2 + 1]);
                    vertexData.putInt(meshBuilder.deformationFromToArray[posIdx * 2]);
                    vertexData.putInt(meshBuilder.deformationFromToArray[posIdx * 2 + 1]);
                    vertexData.putInt(partIndex);
                }

                partIndex++;
            }

            for (int i = 0; i < meshBuilder.affectingBoneIdArray.length; i++) {
                weightMappingData.putInt(meshBuilder.affectingBoneIdArray[i]);
                weightMappingData.putFloat(meshBuilder.weights[meshBuilder.affectingWeightIndexArray[i]]);
            }

            vertexData.flip();
            weightMappingData.flip();

            ComputeBackend backend = ComputeBackends.get();

            vertices = backend.createStorageBuffer(() -> "akythera:mesh_vertices", vertexData);
            boneWeightMapping = backend.createStorageBuffer(() -> "akythera:bone_weights", weightMappingData);
            output = backend.createVertexStorageBuffer(
                () -> "akythera:skinned_vertices", (long) outputVertexStride * vertexCount
            );
        } finally {
            MemoryUtil.memFree(vertexData);
            MemoryUtil.memFree(weightMappingData);
        }
    }

    /// Skins every vertex into [#output] and, where the backend produces one, [#outlineOutput].
    public abstract void skin(SkinnedDrawContext context);

    /// The skinned vertices in the layout the entity pipeline's `VertexFormat` declares.
    public final GpuBuffer output() {
        return output;
    }

    /// The skinned vertices in the outline pipeline's `VertexFormat`, or null when the backend does
    /// not produce them.
    public @Nullable GpuBuffer outlineOutput() {
        return null;
    }

    public final TriangleDepthSort depthSort() {
        if (depthSort == null) {
            depthSort = new TriangleDepthSort(vertexCount);
        }

        return depthSort;
    }

    public final int vertexCount() {
        return vertexCount;
    }

    protected final GpuBuffer vertices() {
        return vertices;
    }

    protected final GpuBuffer boneWeightMapping() {
        return boneWeightMapping;
    }

    protected final int workGroupCount() {
        return (vertexCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
    }

    @Override
    public void close() {
        vertices.close();
        boneWeightMapping.close();
        output.close();

        if (depthSort != null) depthSort.close();
    }
}
