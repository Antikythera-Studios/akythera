package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.physics.cloth.ClothTopology;
import com.yesman.akythera.client.core.shader.backend.ComputeBackend;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.core.Akythera;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/// The immutable GPU residency of one cloth over one [SkeletalMesh]: particles with the offset
/// particles after them, constraints, normal adjacency and the render map. Output and skinning sources
/// are the mesh's own [AnimationComputeShader] buffers.
public final class ClothComputeShader implements AutoCloseable {
    /// Storage buffers the widest cloth program binds, `vanilla_cloth_transformer.comp`.
    public static final int REQUIRED_STORAGE_BLOCKS = 8;

    /// rest xyz + inverse mass, bone from/to, weight, tether anchor, tether length, adjacency start/count,
    /// root (-1 for a particle)
    private static final int PARTICLE_STRIDE = 48;

    /// a, b, rest length, kind
    private static final int CONSTRAINT_STRIDE = 16;

    /// one particle index of a triangle triple
    private static final int INDEX_STRIDE = 4;

    private final SkeletalMesh mesh;
    private final AnimationComputeShader meshShader;
    private final ClothTopology topology;

    private final GpuBuffer particles;
    private final GpuBuffer constraints;
    private final GpuBuffer triangles;
    private final GpuBuffer renderMap;

    public ClothComputeShader(final SkeletalMesh mesh, final float[] weights, final int[] offsetPairs) {
        AnimationComputeShader skinning = mesh.getAnimationComputeShader();

        if (skinning == null) {
            throw new IllegalStateException("A cloth needs the compute skinning residency of its mesh");
        }

        this.mesh = mesh;
        this.meshShader = skinning;
        this.topology = ClothTopology.build(mesh, weights, offsetPairs);

        int particleCount = topology.particleCount();
        int offsetCount = topology.offsetCount();
        int constraintCount = topology.constraintCount();
        int[] adjacentTriangles = topology.adjacentTriangles();
        int[] map = topology.renderMap();

        // Calloc'd and uploaded whole, so padding and the one-element floor of an empty set read zero
        ByteBuffer particleData = MemoryUtil.memCalloc(Math.max(1, particleCount + offsetCount) * PARTICLE_STRIDE);
        ByteBuffer constraintData = MemoryUtil.memCalloc(Math.max(1, constraintCount) * CONSTRAINT_STRIDE);
        ByteBuffer triangleData = MemoryUtil.memCalloc(Math.max(3, adjacentTriangles.length) * INDEX_STRIDE);
        ByteBuffer renderMapData = MemoryUtil.memCalloc(Math.max(1, map.length) * INDEX_STRIDE);

        try {
            float[] rest = topology.restPositions();

            for (int p = 0; p < particleCount; p++) {
                int position = topology.particlePositions()[p];

                particleData
                    .putFloat(rest[p * 3]).putFloat(rest[p * 3 + 1]).putFloat(rest[p * 3 + 2])
                    .putFloat(topology.inverseMasses()[p])
                    .putInt(mesh.getDeformationRangeStart(position))
                    .putInt(mesh.getDeformationRangeEnd(position))
                    .putFloat(topology.weights()[p])
                    .putInt(topology.tetherAnchors()[p])
                    .putFloat(topology.tetherLengths()[p])
                    .putInt(topology.adjacencyStarts()[p])
                    .putInt(topology.adjacencyCounts()[p])
                    .putInt(-1);
            }

            Vector3f offsetRest = new Vector3f();

            for (int o = 0; o < offsetCount; o++) {
                int position = topology.offsetPositions()[o];

                mesh.getPosition(position, offsetRest);
                particleData
                    .putFloat(offsetRest.x).putFloat(offsetRest.y).putFloat(offsetRest.z)
                    .putFloat(0.0F)
                    .putInt(mesh.getDeformationRangeStart(position))
                    .putInt(mesh.getDeformationRangeEnd(position))
                    .putFloat(0.0F)
                    .putInt(-1)
                    .putFloat(0.0F)
                    .putInt(0)
                    .putInt(0)
                    .putInt(topology.offsetRoots()[o]);
            }

            for (int k = 0; k < constraintCount; k++) {
                constraintData
                    .putInt(topology.constraintEnds()[k * 2])
                    .putInt(topology.constraintEnds()[k * 2 + 1])
                    .putFloat(topology.restLengths()[k])
                    .putInt(topology.constraintKinds()[k]);
            }

            for (int index : adjacentTriangles) {
                triangleData.putInt(index);
            }

            for (int particle : map) {
                renderMapData.putInt(particle);
            }

            particleData.clear();
            constraintData.clear();
            triangleData.clear();
            renderMapData.clear();

            ComputeBackend backend = ComputeBackends.get();

            particles = backend.createStorageBuffer(() -> Akythera.prefix("cloth_particles"), particleData);
            constraints = backend.createStorageBuffer(() -> Akythera.prefix("cloth_constraints"), constraintData);
            triangles = backend.createStorageBuffer(() -> Akythera.prefix("cloth_triangles"), triangleData);
            renderMap = backend.createStorageBuffer(() -> Akythera.prefix("cloth_render_map"), renderMapData);
        } finally {
            MemoryUtil.memFree(particleData);
            MemoryUtil.memFree(constraintData);
            MemoryUtil.memFree(triangleData);
            MemoryUtil.memFree(renderMapData);
        }
    }

    public ClothSimulationInstance createInstance() {
        return new ClothSimulationInstance(this);
    }

    /// Writes every vertex of the mesh into its [AnimationComputeShader] outputs: simulated ones from
    /// `instance`'s state, the rest skinned. `drawParams` is the slice `PatchModelRenderer` writes.
    public void skin(final ClothSimulationInstance instance, final GpuBufferSlice poses, final GpuBufferSlice drawParams) {
        GpuBuffer outline = meshShader.outlineOutput();

        if (outline == null) {
            throw new IllegalStateException("The cloth transformer writes the vanilla outline output, which this backend does not produce");
        }

        try (ComputeProgram.Dispatch dispatch = ClothProgram.VANILLA_TRANSFORMER.get().begin()) {
            dispatch.bindStorageBuffer(0, poses)
                .bindStorageBuffer(1, meshShader.vertices())
                .bindStorageBuffer(2, meshShader.boneWeightMapping())
                .bindStorageBuffer(3, drawParams)
                .bindStorageBuffer(4, meshShader.output())
                .bindStorageBuffer(5, outline)
                .bindStorageBuffer(6, renderMap)
                .bindStorageBuffer(7, instance.state())
                .dispatch(meshShader.workGroupCount(), 1, 1);
        }
    }

    public SkeletalMesh getMesh() {
        return mesh;
    }

    public AnimationComputeShader getMeshShader() {
        return meshShader;
    }

    public ClothTopology getTopology() {
        return topology;
    }

    GpuBuffer particles() {
        return particles;
    }

    GpuBuffer constraints() {
        return constraints;
    }

    GpuBuffer triangles() {
        return triangles;
    }

    GpuBuffer boneWeights() {
        return meshShader.boneWeightMapping();
    }

    @Override
    public void close() {
        particles.close();
        constraints.close();
        triangles.close();
        renderMap.close();
    }
}
