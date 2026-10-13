package com.yesman.akythera.client.core.asset.mesh;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    protected final MeshLoader.Metadata metadata;

    public StaticMesh(CommonMeshBuilder<StaticMeshPart> meshBuilder) {
        if (meshBuilder.positions == null) {
            throw new NoSuchElementException("No position array is declared in mesh file!");
        }

        if (meshBuilder.normals == null) {
            throw new NoSuchElementException("No normals array is declared in mesh file!");
        }

        if (meshBuilder.uvs == null) {
            throw new NoSuchElementException("No uv array is declared in mesh file!");
        }

        metadata = meshBuilder.metadata;

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

        ImmutableMap.Builder<@NonNull String, @NonNull StaticMeshPart> meshPartBuilder = ImmutableMap.builder();

        for (Map.Entry<String, List<VertexBuilder>> entry : meshBuilder.partBuilders.entrySet()) {
            meshPartBuilder.put(entry.getKey(), new StaticMeshPart(entry.getValue(), entry.getKey()));
        }

        meshParts = meshPartBuilder.build();
    }

    @Override
    public Map<String, StaticMeshPart> getParts() {
        return meshParts;
    }

    @Override
    public MeshLoader.Metadata getMetadata() {
        return metadata;
    }

    @Override
    public void close() {
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
        private final String name;
        private final int vertexCount;
        private boolean visible = true;

        private StaticMeshPart(final List<VertexBuilder> vertexBuilders, final String name) {
            this.name = name;
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
            PoseStack.Pose pose,
            VertexConsumer bufferBuilder,
            DrawingFunction drawingFunction,
            int packedLight,
            float r,
            float g,
            float b,
            float a,
            int overlay
        ) {
            Matrix4f poseMatrix = pose.pose();
            Matrix3f normalMatrix = pose.normal();

            for (int i = 0; i < vertexCount; i++) {
                int positionIdx = drawIndices.getInt(Mesh.byteIndex(i * 3));
                int normalIdx = drawIndices.getInt(Mesh.byteIndex(i * 3 + 1));
                int uvIdx = drawIndices.getInt(Mesh.byteIndex(i * 3 + 2));

                Vector4f position = new Vector4f(
                    positions.getFloat(Mesh.byteIndex(positionIdx * 3)),
                    positions.getFloat(Mesh.byteIndex(positionIdx * 3 + 1)),
                    positions.getFloat(Mesh.byteIndex(positionIdx * 3 + 2)),
                    1.0F
                );
                Vector3f normal = new Vector3f(
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3)),
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3 + 1)),
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3 + 2))
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
                    uvs.getFloat(Mesh.byteIndex(uvIdx * 2)),
                    uvs.getFloat(Mesh.byteIndex(uvIdx * 2 + 1)),
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

        @Override
        public String partName() {
            return name;
        }
    }

    public static CommonMeshBuilder<StaticMeshPart> builder() {
        return new CommonMeshBuilder<>();
    }
}
