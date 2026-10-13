package com.yesman.akythera.client.core.renderer;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.core.Akythera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

public final class RenderUtils {
    /// The depth-only pass of [#drawBuffers]: `entity_translucent`'s shader and alpha cutout, writing depth and no
    /// color.
    private static final RenderPipeline FRONTMOST_DEPTH = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(Akythera.identifier("pipeline/frontmost_depth"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
        .withCull(false)
        .build();

    private RenderUtils() {
    }

    /// Entity render space to the armature's model space: undoes the y offset and the axis flip
    /// [LivingEntityRenderer#submit] applies before it hands the model over.
    public static void toArmatureSpace(final PoseStack poseStack) {
        poseStack.translate(0.0F, -EntityModel.MODEL_Y_OFFSET, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
    }

    /// Draws `meshData` through `renderType` on the spot, in the order of its sorted index buffer when it has one
    /// ([MeshData#sortQuads]).
    ///
    /// The body of [PreparedRenderType#drawFromBuffer] with its whole-buffer vertex binding replaced
    /// by the transient slice the upload hands back. Geometry built inside the feature-render phase
    /// belongs in `FeatureFrameContext#stagedVertexBuffer` instead — this is for callers that never
    /// reach it, which since 26.2 have no other route to the GPU.
    public static void drawImmediate(final RenderType renderType, final MeshData meshData) {
        drawImmediate(renderType, meshData, false);
    }

    /// [#drawImmediate(RenderType, MeshData)], coloring only the fragments nearest the camera when `frontmost`, see
    /// [#drawBuffers].
    public static void drawImmediate(final RenderType renderType, final MeshData meshData, final boolean frontmost) {
        MeshData.DrawState drawState = meshData.drawState();
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        // Both reach the command encoder—the upload writes through it, and the sequential buffer
        // allocates when it has to grow. It rejects either while a render pass is open, so they run
        // before the pass opens.
        GpuBufferSlice vertices = commandEncoder.transientMemory()
            .uploadGpu(meshData.vertexBuffer(), drawState.format().getVertexSize(), GpuBuffer.USAGE_VERTEX);
        ByteBuffer sortedIndices = meshData.indexBuffer();

        if (sortedIndices != null) {
            GpuBufferSlice uploaded = commandEncoder.transientMemory().uploadGpu(sortedIndices, drawState.indexType().bytes, GpuBuffer.USAGE_INDEX);
            IndexType indexType = drawState.indexType();

            drawBuffers(renderType, vertices, uploaded.buffer(), indexType, (int)(uploaded.offset() / indexType.bytes), drawState.indexCount(), frontmost);
        } else {
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());

            drawBuffers(renderType, vertices, indices.getBuffer(drawState.indexCount()), indices.type(), 0, drawState.indexCount(), frontmost);
        }
    }

    /// Draws `indexCount` indices of `indexBuffer` from `firstIndex`, over `vertices`, through `renderType`.
    ///
    /// When `frontmost`, a depth-only pass runs first, with `renderType`'s textures and the entity pipelines' alpha
    /// cutout ([#FRONTMOST_DEPTH]). The color pass then colors only the fragments nearest the camera, so a translucent
    /// model shows none of its own faces behind them and is equally see-through all over. `renderType` must take
    /// [DefaultVertexFormat#ENTITY] vertices.
    public static void drawBuffers(
        final RenderType renderType,
        final GpuBufferSlice vertices,
        final GpuBuffer indexBuffer,
        final IndexType indexType,
        final int firstIndex,
        final int indexCount,
        final boolean frontmost
    ) {
        PreparedRenderType prepared = renderType.prepare();

        if (frontmost) drawPass(prepared, FRONTMOST_DEPTH, vertices, indexBuffer, indexType, firstIndex, indexCount);

        drawPass(prepared, prepared.pipeline(), vertices, indexBuffer, indexType, firstIndex, indexCount);
    }

    /// One render pass drawing through `pipeline` with `prepared`'s target, uniforms and textures.
    private static void drawPass(
        final PreparedRenderType prepared,
        final RenderPipeline pipeline,
        final GpuBufferSlice vertices,
        final GpuBuffer indexBuffer,
        final IndexType indexType,
        final int firstIndex,
        final int indexCount
    ) {
        RenderTarget renderTarget = prepared.outputTarget().getRenderTarget();
        GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
            ? RenderSystem.outputColorTextureOverride
            : renderTarget.getColorTextureView();
        GpuTextureView depthTexture = renderTarget.useDepth
            ? (RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride
                : renderTarget.getDepthTextureView())
            : null;

        try (
            RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Akythera immediate draw with " + pipeline,
                colorTexture,
                Optional.empty(),
                depthTexture,
                OptionalDouble.empty()
            )
        ) {
            renderPass.setPipeline(pipeline);

            ScissorState scissorState = prepared.scissorState();

            if (scissorState.enabled()) {
                renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
            }

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", prepared.dynamicTransforms());
            renderPass.setVertexBuffer(0, vertices);

            for (PreparedRenderType.Texture texture : prepared.textures()) {
                renderPass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
            }

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(indexCount, 1, firstIndex, 0, 0);
        }
    }

    /// The renderer an entity type is drawn by, or null when it has none.
    ///
    /// `EntityRenderDispatcher` keys its renderers by [EntityType] behind a private field, and
    /// `EntityRenderers`' provider map needs a render context to build one — but
    /// `EntityRenderDispatcher#getRenderer(EntityRenderState)` is public and reads nothing off the
    /// state except [EntityRenderState#entityType], so a probe state carrying only the type resolves
    /// the renderer.
    ///
    /// The player and the mannequin are absent from that type-keyed map altogether
    /// (`EntityRenderers#validateRegistrations` exempts them, and the dispatcher keeps them in
    /// `PlayerModelType`-keyed maps instead); both are reached by the [AvatarRenderState] branch of
    /// `getRenderer`, which is why the probe's runtime type matters.
    public static @Nullable EntityRenderer<?, ?> rendererOf(final EntityType<?> entityType) {
        EntityRenderState probe = entityType == EntityTypes.PLAYER || entityType == EntityTypes.MANNEQUIN
            ? new AvatarRenderState()
            : new EntityRenderState();

        probe.entityType = entityType;

        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(probe);
    }

    /// The [EntityRenderState] the entity type registered under `entityTypeId` is drawn through, or
    /// null when the id names no entity type or that type has no renderer.
    ///
    /// An entity patch is looked up by its entity type's id (`ComponentsFactory#getEntityPatch`), so a
    /// patch's own registry id is what this takes.
    public static @Nullable Class<? extends EntityRenderState> renderStateOf(final Identifier entityTypeId) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) return null;

        EntityRenderer<?, ?> renderer = rendererOf(BuiltInRegistries.ENTITY_TYPE.getValue(entityTypeId));

        return renderer == null ? null : renderer.createRenderState().getClass();
    }

    /// The entity class an entity type is built from, or [Entity] when nothing narrower shows.
    ///
    /// Read off the renderer because vanilla exposes no `EntityType -> Class`: `EntityType#getBaseClass`
    /// answers [Entity] for every type, and `EntityType#create` returns null for `minecraft:player`.
    /// [AvatarRenderState] is built only for avatars and [LivingEntityRenderState] only for living
    /// entities, so the state class the renderer makes names the tier.
    ///
    /// A mannequin is an `Avatar` without being a [Player] and renders as one, so it reads one tier
    /// wider than it is.
    public static Class<? extends Entity> entityClassOf(final Identifier entityTypeId) {
        Class<? extends EntityRenderState> renderState = renderStateOf(entityTypeId);

        if (renderState == null) return Entity.class;
        if (AvatarRenderState.class.isAssignableFrom(renderState)) return Player.class;
        if (LivingEntityRenderState.class.isAssignableFrom(renderState)) return LivingEntity.class;

        return Entity.class;
    }
}