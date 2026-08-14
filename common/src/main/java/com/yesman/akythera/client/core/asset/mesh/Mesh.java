package com.yesman.akythera.client.core.asset.mesh;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/// Classic 3D models created in Blender which are independent of any Minecraft cube based models
///
/// Supports binding buffers to shaders.
///
/// Beware that these models are incompatible with Minecraft render pipelines (e.g. render layers)
/// due to the differences in coordinate systems.
public interface Mesh<T extends Mesh.MeshPart> extends AutoCloseable {
    /// Renders the whole mesh with a common render type
    default void draw(
        PoseStack.Pose pose,
        VertexConsumer vertexConsumer,
        DrawingFunction drawingFunction,
        int packedLight,
        float r,
        float g,
        float b,
        float a,
        int overlay
    ) {
        for (T part : getParts().values()) {
            if (part.visible()) part.draw(pose, vertexConsumer, drawingFunction, packedLight, r, g, b, a, overlay);
        }
    }

    /// Render the mesh with different render types applying per [MeshPart].
    ///
    /// Mesh parts are distinguished by names
    default void drawWithMultiRenderTypes(
        Map<String, RenderType> renderTypes,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        DrawingFunction drawingFunction,
        int packedLight,
        float r,
        float g,
        float b,
        float a,
        int overlay
    ) {
        for (Map.Entry<String, T> entry : getParts().entrySet()) {
            if (entry.getValue().visible()) {
                submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    renderTypes.get(entry.getKey()),
                    (pose, buffer) -> draw(
                        pose,
                        buffer,
                        Mesh.DrawingFunction.NEW_ENTITY,
                        packedLight,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        OverlayTexture.NO_OVERLAY
                    )
                );
            }
        }
    }

    Map<String, T> getParts();

    /// Used by creating *convenient access* fields. Throw exception for the fields that require non-null
    default T getPart(String name, Consumer<NoSuchElementException> exceptionHandler) {
        if (!getParts().containsKey(name)) {
            exceptionHandler.accept(new NoSuchElementException("No part named " + name));
            return null;
        }

        return getParts().get(name);
    }

    /// Called on resource reloading event to destroy existing buffers
    void close();

    /// Allows showing & hiding a part from a whole mesh dynamically
    interface MeshPart {
        /// Draws the part without graphic boosting solutions, or compute shaders.
        /// Just use Minecraft pipeline in raw for the best compatibility.
        void draw(
            PoseStack.Pose pose,
            VertexConsumer bufferBuilder,
            DrawingFunction drawingFunction,
            int packedLight,
            float r,
            float g,
            float b,
            float a,
            int overlay
        );

        /// Sets visibility of the part
        void setVisible(boolean b);

        /// Returns whether the part is visible
        boolean visible();

        String partName();
    }

    /// Modularized vertex builders for [VertexFormat], allowing to be rendered by any [RenderType]
    @FunctionalInterface
    interface DrawingFunction {
        /// Paired with [DefaultVertexFormat#ENTITY]
        DrawingFunction NEW_ENTITY = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(packedLight)
                .setNormal(normX, normY, normZ);
        };

        /// Paired with [DefaultVertexFormat#POSITION_TEX]
        DrawingFunction POSITION_TEX = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setUv(u, v);
        };

        /// Paired with [DefaultVertexFormat#POSITION_TEX_COLOR_NORMAL]
        DrawingFunction POSITION_TEX_COLOR_NORMAL = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setUv(u, v)
                .setColor(r, g, b, a)
                .setNormal(normX, normY, normZ);
        };

        /// Paired with [DefaultVertexFormat#POSITION_COLOR_LIGHTMAP]
        DrawingFunction POSITION_COLOR_LIGHTMAP = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setColor(r, g, b, a)
                .setLight(packedLight);
        };

        /// Paired with [DefaultVertexFormat#POSITION_COLOR_NORMAL]
        DrawingFunction POSITION_COLOR_NORMAL = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setColor(r, g, b, a)
                .setNormal(normX, normY, normZ);
        };

        /// Paired with [DefaultVertexFormat#POSITION_COLOR_TEX_LIGHTMAP]
        DrawingFunction POSITION_COLOR_TEX_LIGHTMAP = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
            builder.addVertex(posX, posY, posZ)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setLight(packedLight);
        };

        void draw(VertexConsumer vertexConsumer, float posX, float posY, float posZ, float normX, float normY, float normZ, int packedLight, float r, float g, float b, float a, float u, float v, int overlay);
    }

    MeshLoader.Metadata getMetadata();

    /// The byte offset element `elementIndex` sits at in the vertex `ByteBuf`s a mesh keeps. They
    /// hold 4-byte elements throughout, and Netty's absolute getters address bytes.
    static int byteIndex(final int elementIndex) {
        return elementIndex * 4;
    }

    class CommonMeshBuilder<T extends MeshPart> {
        public Map<String, List<VertexBuilder>> partBuilders = new HashMap<>();
        public float[] positions;
        public float[] normals;
        public float[] uvs;
        public MeshLoader.Metadata metadata = MeshLoader.Metadata.DEFAULT;

        /// Adds a new part
        public CommonMeshBuilder<T> addPart(String partName, List<VertexBuilder> vertexBuilders) {
            partBuilders.put(partName, vertexBuilders);
            return this;
        }

        /// Primitive position array with 3 position vector elements
        public CommonMeshBuilder<T> positions(float[] positions) {
            this.positions = positions;
            return this;
        }

        /// Primitive normal array with 3 normal vector elements
        public CommonMeshBuilder<T> normals(float[] normals) {
            this.normals = normals;
            return this;
        }

        /// Primitive uv array with 2 uv coord vector elements
        public CommonMeshBuilder<T> uvs(float[] uvs) {
            this.uvs = uvs;
            return this;
        }

        /// The asset's sidecar metadata, see [MeshLoader#metadata]
        public CommonMeshBuilder<T> metadata(MeshLoader.Metadata metadata) {
            this.metadata = metadata;
            return this;
        }
    }
}
