package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.ComputeShaderLoader;
import com.yesman.akythera.client.core.shader.backend.program.BarrierFlags;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/// Skinning against the vanilla shader system: the output is exactly what
/// `assets/minecraft/shaders/core/entity.vsh` reads, so nothing about the render pipeline changes.
public final class VanillaAnimationComputeShader extends AnimationComputeShader {
    /// [DefaultVertexFormat#ENTITY]: Position(3×float) Color(4×ubyte) UV0(2×float) UV1(2×short)
    /// UV2(2×short) Normal(3×byte) + 1 pad.
    private static final int OUTPUT_VERTEX_STRIDE = 36;

    /// [DefaultVertexFormat#POSITION_TEX_COLOR], the format `RenderPipelines.OUTLINE_*` declares.
    private static final int OUTLINE_VERTEX_STRIDE = 24;

    private static final Identifier PROGRAM_ID =
        Identifier.fromNamespaceAndPath(Akythera.MODID, "shaders/compute/vanilla_mesh_transformer.comp");

    private static @Nullable ComputeProgram program;

    private final GpuBuffer outlineOutput;

    public VanillaAnimationComputeShader(final SkeletalMesh.SkeletalMeshBuilder meshBuilder) {
        super(meshBuilder, OUTPUT_VERTEX_STRIDE);

        outlineOutput = ComputeBackends.get().createVertexStorageBuffer(
            () -> "akythera:skinned_outline_vertices", (long) OUTLINE_VERTEX_STRIDE * vertexCount()
        );
    }

    /// Drops the shared program so the next dispatch recompiles it from the reloaded pack.
    public static void invalidateProgram() {
        if (program != null) {
            program.close();
            program = null;
        }
    }

    @Override
    public void skin(final SkinnedDrawContext context) {
        ComputeProgram shader = program();

        // The color attribute is four normalized unsigned bytes read as RGBA, so the packed word the
        // shader stores has red in its low byte — which is what ARGB spells the other way round.
        shader.uniform("colorIn").putUnsignedInt(ARGB.toABGR(context.color()));
        shader.uniform("outlineColorIn").putUnsignedInt(ARGB.toABGR(context.outlineColor()));
        shader.uniform("uv1In").putUnsignedInt(context.packedOverlay());
        shader.uniform("uv2In").putUnsignedInt(context.packedLight());
        shader.uniform("model_view_matrix").putMatrix4f(context.modelView());
        shader.uniform("normal_matrix").putMatrix3f(context.normal());

        try (ComputeProgram.Dispatch dispatch = shader.begin()) {
            dispatch.bindStorageBuffer(0, context.poses())
                .bindStorageBuffer(1, vertices())
                .bindStorageBuffer(2, boneWeightMapping())
                .bindStorageBuffer(3, context.visibilityFlags())
                .bindStorageBuffer(4, output())
                .bindStorageBuffer(5, outlineOutput)
                .dispatch(workGroupCount(), 1, 1);
        }
    }

    @Override
    public GpuBuffer outlineOutput() {
        return outlineOutput;
    }

    @Override
    public void close() {
        super.close();
        outlineOutput.close();
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
}
