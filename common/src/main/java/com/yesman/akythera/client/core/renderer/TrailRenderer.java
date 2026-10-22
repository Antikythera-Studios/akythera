package com.yesman.akythera.client.core.renderer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.ComputeShaderLoader;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.setup.DynamicStorageBuffer;
import com.yesman.akythera.core.Akythera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// Draws the trails queued in a frame: one compute pass (`trail_mesh.comp`) builds every trail's quads into one vertex
/// buffer, then each trail is drawn from its slice. Draws nothing where compute is unsupported.
public final class TrailRenderer {
    private static final TrailRenderer INSTANCE = new TrailRenderer();

    private static final Identifier PROGRAM_ID = Akythera.identifier("shaders/compute/trail_mesh.comp");

    /// Invocations per work group, matching `layout(local_size_x)` in the compute source.
    private static final int WORK_GROUP_SIZE = 64;

    /// `Header` of the compute source: five 4-byte words.
    private static final int HEADER_BYTES = 20;

    /// Floats of one control in [Trail#controls], the `Control` of the compute source: two positions, u and alpha.
    public static final int CONTROL_FLOATS = 8;

    /// [com.mojang.blaze3d.vertex.DefaultVertexFormat#PARTICLE].
    private static final int VERTEX_BYTES = 28;

    /// The particle shader, translucent, two-sided, depth-tested without writing depth.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
        .withLocation(Akythera.identifier("pipeline/trail"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .build();

    private static final Function<Identifier, RenderType> RENDER_TYPES = Util.memoize(
        texture -> RenderType.create(
            "akythera_trail",
            RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                .useLightmap()
                .createRenderSetup()
        )
    );

    private static @Nullable ComputeProgram program;

    public static TrailRenderer getInstance() {
        return INSTANCE;
    }

    /// The render type a trail with `texture` draws with.
    public static RenderType renderType(final Identifier texture) {
        return RENDER_TYPES.apply(texture);
    }

    /// Whether trails are drawn this run.
    public static boolean isAvailable() {
        return ClientComponentsFactory.getImpl().supportComputeShader();
    }

    /// Drops the shared program so the next frame recompiles it from the reloaded pack.
    public static void invalidateProgram() {
        if (program != null) {
            program.close();
            program = null;
        }
    }

    private final List<Trail> queued = new ArrayList<>();

    /// The frame's headers and control edges.
    private @Nullable DynamicStorageBuffer frameStorage;

    /// The quads the compute pass writes. Grows to the largest frame.
    private @Nullable GpuBuffer vertices;

    private TrailRenderer() {
    }

    public void submit(final Trail trail) {
        queued.add(trail);
    }

    /// Builds and draws every queued trail.
    public void render() {
        if (queued.isEmpty() || !isAvailable()) {
            queued.clear();

            return;
        }

        int controlCount = 0;
        int quadCount = 0;
        int maxQuads = 0;

        for (Trail trail : queued) {
            controlCount += trail.controlCount();
            quadCount += trail.quadCount();
            maxQuads = Math.max(maxQuads, trail.quadCount());
        }

        DynamicStorageBuffer storage = frameStorage();
        GpuBufferSlice headers = storage.write(queued.size() * HEADER_BYTES, buffer -> {
            int controlOffset = 0;
            int vertexOffset = 0;

            for (Trail trail : queued) {
                buffer.putInt(controlOffset)
                    .putInt(trail.controlCount())
                    .putInt(vertexOffset)
                    .putInt(ARGB.toABGR(trail.color()))
                    .putInt(trail.lightCoords());

                controlOffset += trail.controlCount();
                vertexOffset += trail.quadCount() * 4;
            }
        });
        GpuBufferSlice controls = storage.write(controlCount * CONTROL_FLOATS * Float.BYTES, buffer -> {
            for (Trail trail : queued) {
                for (int i = 0; i < trail.controlCount() * CONTROL_FLOATS; i++) {
                    buffer.putFloat(trail.controls()[i]);
                }
            }
        });

        long vertexBytes = (long)quadCount * 4 * VERTEX_BYTES;

        if (vertices == null || vertices.size() < vertexBytes) {
            if (vertices != null) vertices.close();

            vertices = ComputeBackends.get().createVertexStorageBuffer(() -> "akythera:trail_vertices", Mth.roundToward((int)vertexBytes, 65536));
        }

        try (ComputeProgram.Dispatch dispatch = program().begin()) {
            dispatch.bindStorageBuffer(0, headers)
                .bindStorageBuffer(1, controls)
                .bindStorageBuffer(2, vertices)
                .dispatch((maxQuads + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, queued.size(), 1);
        }

        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(maxQuads * 6);
        int vertexOffset = 0;

        for (Trail trail : queued) {
            trail.renderType().prepare().drawFromBuffer(vertices, indexBuffer, indices.type(), vertexOffset, 0, trail.quadCount() * 6);
            vertexOffset += trail.quadCount() * 4;
        }

        queued.clear();
    }

    public void clear() {
        queued.clear();
    }

    public void endFrame() {
        if (frameStorage != null) frameStorage.endFrame();
    }

    private DynamicStorageBuffer frameStorage() {
        if (frameStorage == null) {
            frameStorage = new DynamicStorageBuffer("akythera:trail_payloads");
        }

        return frameStorage;
    }

    private static ComputeProgram program() {
        if (program == null) {
            program = ComputeShaderLoader.loadComputeShaderProgram(
                ComponentsFactory.getImpl().getResourceManager(),
                PROGRAM_ID,
                BarrierFlags.SHADER_STORAGE,
                BarrierFlags.VERTEX_ATTRIBUTE
            );
        }

        return program;
    }

    /// One trail's draw: a quad between each two neighboring control edges.
    ///
    /// @param controls     the control edges, oldest first, [#CONTROL_FLOATS] each: the origin side and the far side,
    ///                     camera-relative, then the texture's u and the alpha multiplier
    /// @param controlCount at least 2
    /// @param color        ARGB
    public record Trail(RenderType renderType, float[] controls, int controlCount, int color, int lightCoords) {
        public int quadCount() {
            return controlCount - 1;
        }
    }
}
