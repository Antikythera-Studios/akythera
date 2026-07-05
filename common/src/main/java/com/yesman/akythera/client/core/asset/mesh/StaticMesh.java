package com.yesman.akythera.client.core.asset.mesh;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

/// A classic mesh component with no deformation
///
/// Static meshes can have whatever shape whereas
/// [ModelPart] only accepts cuboid shapes.
///
/// Supports binding vertex buffers
public class StaticMesh implements Mesh<StaticMesh.StaticMeshPart> {
    protected final Map<String, StaticMeshPart> meshParts;

    /// position buffer
    protected final ByteBuf positions;

    /// normal buffer
    protected final ByteBuf normals;

    /// uv buffer
    protected final ByteBuf uvs;

    public StaticMesh(CommonMeshBuilder<StaticMeshPart> meshBuilder) {
        // Declare as direct buffers since memory alloc/dealloc is not frequent,
        // and the length of array is big which overloads GC scanning process if it's a heap buffer.
        positions = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.positions.length * 4);
        normals = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.normals.length * 4);
        uvs = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.uvs.length * 4);

        for (float f : meshBuilder.positions) {
            positions.writeFloat(f);
        }

        for (float f : meshBuilder.normals) {
            normals.writeFloat(f);
        }

        for (float f : meshBuilder.uvs) {
            uvs.writeFloat(f);
        }

        ImmutableMap.Builder<@NotNull String, @NotNull StaticMeshPart> meshPartBuilder = ImmutableMap.builder();

        for (Map.Entry<String, List<VertexBuilder>> entry : meshBuilder.partBuilders.entrySet()) {
            meshPartBuilder.put(entry.getKey(), new StaticMeshPart(entry.getValue()));
        }

        meshParts = meshPartBuilder.build();
    }

    @Override
    public Map<String, StaticMeshPart> getParts() {
        return meshParts;
    }

    @Override
    public void destroy() {
        // Decrease ref count (expected 0) to let GC deallocate these buffers
        positions.release();
        normals.release();
        uvs.release();
        for (StaticMeshPart meshPart : this.meshParts.values()) {
            meshPart.drawIndices.release();
        }
    }

    /// Internal Only: there's no reason to hardcode mesh objects. Import mesh resources via [MeshLoader]
    public class StaticMeshPart implements MeshPart {
        /// A buffer that (position, normal, uv) are in 1D array
        private final ByteBuf drawIndices;
        private final int vertexCount;
        private boolean visible;

        private StaticMeshPart(List<VertexBuilder> vertexBuilders) {
            // A builder = 3 integers = 12 bytes
            drawIndices = ByteBufAllocator.DEFAULT.directBuffer(vertexBuilders.size() * 12);
            vertexCount = vertexBuilders.size();

            for (VertexBuilder vb : vertexBuilders) {
                drawIndices.writeInt(vb.position());
                drawIndices.writeInt(vb.normal());
                drawIndices.writeInt(vb.uv());
            }
        }

        @Override
        public void draw(
            PoseStack poseStack,
            VertexConsumer bufferBuilder,
            DrawingFunction drawingFunction,
            int packedLight,
            float r,
            float g,
            float b,
            float a,
            int overlay
        ) {
            Matrix4f poseMatrix = poseStack.last().pose();
            Matrix3f normalMatrix = poseStack.last().normal();

            for (int i = 0; i < vertexCount; i++) {
                int positionIdx = drawIndices.getInt(i*3);
                int normalIdx = drawIndices.getInt(i*3+1);
                int uvIdx = drawIndices.getInt(i*3+2);

                Vector4f position = new Vector4f(
                    positions.getFloat(positionIdx*3),
                    positions.getFloat(positionIdx*3+1),
                    positions.getFloat(positionIdx*3+2),
                    1.0F
                );
                Vector3f normal = new Vector3f(
                    normals.getFloat(normalIdx*3),
                    normals.getFloat(normalIdx*3+1),
                    normals.getFloat(normalIdx*3+2)
                );

                position.mul(poseMatrix);
                normal.mul(normalMatrix);

                drawingFunction.draw(
                    bufferBuilder,
                    position.x(),
                    position.y(),
                    position.z(),
                    normal.x(),
                    normal.y(),
                    normal.z(),
                    packedLight,
                    r,g,b,a,
                    uvs.getFloat(uvIdx*2),
                    uvs.getFloat(uvIdx*2+1),
                    overlay
                );
            }
        }

        @Override
        public void setVisible(boolean b) {
            visible = b;
        }

        @Override
        public boolean visible() {
            return visible;
        }
    }

    public static CommonMeshBuilder<StaticMeshPart> builder() {
        return new CommonMeshBuilder<>();
    }
}
