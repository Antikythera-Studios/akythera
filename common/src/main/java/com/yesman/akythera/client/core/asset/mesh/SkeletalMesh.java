package com.yesman.akythera.client.core.asset.mesh;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.client.core.shader.backend.setup.AnimationComputeShader;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

/// A mesh with skeletal bone information, allowing mesh deformation
/// in runtime.
///
/// Provides frame boosts when compute shader feature is turned on, by
/// calculating an animation space vertex position in GPU.
///
/// Allowed inheriting this class for convenient access to bones.
public class SkeletalMesh implements Mesh<SkeletalMesh.SkeletalMeshPart> {
    protected final Map<String, SkeletalMeshPart> meshParts;

    /// Position buffer (3d vector)
    protected final ByteBuf positions;

    /// Normal buffer (3d vector)
    protected final ByteBuf normals;

    /// Uv buffer (2d vector)
    protected final ByteBuf uvs;

    /// Deformation weight buffer (single element)
    ///
    /// This is a source buffer like positions, normals, and uvs
    protected final ByteBuf weights;

    /// A buffer with number of bones involved in deforming a vertex (2d vector)
    ///
    /// The indices are bound to [#positions], which defines the vertices
    protected final ByteBuf deformationFromToIndices;

    /// A buffer with bone indices
    /// strides are between 1-3, depending on [#deformationFromToIndices]
    protected final ByteBuf deformBoneIndices;

    /// A buffer with weight indices, referencing [#weights]
    /// strides are between 1-3, depending on [#deformationFromToIndices]
    protected final ByteBuf deformWeightIndices;

    /// Buffers to be used in compute shader. Assigned only if compute shader is supported by device.
    protected @Nullable AnimationComputeShader animationComputeShader;

    protected final MeshLoader.Metadata metadata;

    public SkeletalMesh(final SkeletalMeshBuilder meshBuilder) {
        metadata = meshBuilder.metadata;

        // Declare as direct buffers since memory alloc/dealloc is not frequent,
        // and the length of array is big which overloads GC scanning process if it's a heap buffer.
        positions = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.positions.length * 4);
        normals = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.normals.length * 4);
        uvs = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.uvs.length * 4);
        weights = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.weights.length * 4);
        deformationFromToIndices = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.deformationFromToArray.length * 4);
        deformBoneIndices = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.affectingBoneIdArray.length * 4);
        deformWeightIndices = ByteBufAllocator.DEFAULT.directBuffer(meshBuilder.affectingWeightIndexArray.length * 4);

        for (float f : meshBuilder.positions) {
            positions.writeFloat(f);
        }

        for (float f : meshBuilder.normals) {
            normals.writeFloat(f);
        }

        for (float f : meshBuilder.uvs) {
            uvs.writeFloat(f);
        }

        for (float f : meshBuilder.weights) {
            weights.writeFloat(f);
        }

        for (int i : meshBuilder.deformationFromToArray) {
            deformationFromToIndices.writeInt(i);
        }

        for (int i : meshBuilder.affectingBoneIdArray) {
            deformBoneIndices.writeInt(i);
        }

        for (int i : meshBuilder.affectingWeightIndexArray) {
            deformWeightIndices.writeInt(i);
        }

        ImmutableMap.Builder<@NotNull String, @NotNull SkeletalMeshPart> meshPartBuilder = ImmutableMap.builder();

        for (Map.Entry<String, List<VertexBuilder>> entry : meshBuilder.partBuilders.entrySet()) {
            meshPartBuilder.put(entry.getKey(), new SkeletalMeshPart(entry.getValue(), entry.getKey()));
        }

        meshParts = meshPartBuilder.build();

        animationComputeShader = ClientComponentsFactory.getImpl().getAnimationComputeShaderSetup(meshBuilder);
    }

    /// Draw a mesh with mesh deformation, by the bone transform in a specific pose.
    ///
    /// Requires **fully** bound transform matrices, by [Pose#getForwardBoundTransforms] x [Armature#boundBackwardTransform]
    /// 
    /// @see Pose#getForwardBoundTransforms the definition of forward binding
    /// @see Armature#boundBackwardTransform the definition of backward binding
    public void drawPosed(
        final PoseStack poseStack,
        final VertexConsumer bufferBuilder,
        final DrawingFunction drawingFunction,
        final int packedLight,
        final float r,
        final float g,
        final float b,
        final float a,
        final int overlay,
        final Matrix4f[] poses
    ) {
        for (SkeletalMeshPart part : getParts().values()) {
            if (part.visible()) part.drawPosed(poseStack, bufferBuilder, drawingFunction, packedLight, r, g, b, a, overlay, poses);
        }
    }

    /// The mesh's GPU residency for the skinning dispatch, or null when the device has no compute
    /// support.
    public @Nullable AnimationComputeShader getAnimationComputeShader() {
        return animationComputeShader;
    }

    @Override
    public Map<String, SkeletalMeshPart> getParts() {
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
        weights.release();
        deformationFromToIndices.release();
        deformBoneIndices.release();
        deformWeightIndices.release();

        for (SkeletalMeshPart meshPart : this.meshParts.values()) {
            meshPart.drawIndices.release();
        }

        if (animationComputeShader != null) animationComputeShader.close();
    }

    public class SkeletalMeshPart implements MeshPart {
        /// A buffer that saves (position, normal, uv) in 1D array
        private final ByteBuf drawIndices;
        private final String name;
        private final int vertexCount;
        private boolean visible = true;

        private SkeletalMeshPart(final List<VertexBuilder> vertexBuilders, final String name) {
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
            final PoseStack.Pose pose,
            final VertexConsumer bufferBuilder,
            final DrawingFunction drawingFunction,
            final int packedLight,
            final float r,
            final float g,
            final float b,
            final float a,
            final int overlay
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

        /// The CPU counterpart of `assets/akythera/shaders/compute/vanilla_mesh_transformer.comp`,
        /// vertex for vertex. Used where the device has no compute support.
        public void drawPosed(
            final PoseStack poseStack,
            final VertexConsumer bufferBuilder,
            final DrawingFunction drawingFunction,
            final int packedLight,
            final float r,
            final float g,
            final float b,
            final float a,
            final int overlay,
            final Matrix4f[] poses
        ) {
            Matrix4f poseMatrix = poseStack.last().pose();
            Matrix3f normalMatrix = poseStack.last().normal();
            Vector4f scratch = new Vector4f();

            // Expensive part: CPU pose upload—If GPU driver supports, use Compute Shader feature(#computeShaderPatch)
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
                // w = 0: a direction, so the bone matrices' translation column must not reach it
                Vector4f normal = new Vector4f(
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3)),
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3 + 1)),
                    normals.getFloat(Mesh.byteIndex(normalIdx * 3 + 2)),
                    0.0F
                ).normalize3();

                Vector4f positionTotal = new Vector4f();
                Vector3f normalTotal = new Vector3f();
                int start = deformationFromToIndices.getInt(Mesh.byteIndex(positionIdx * 2));
                int end = deformationFromToIndices.getInt(Mesh.byteIndex(positionIdx * 2 + 1));

                // Apply transforms of affecting bones
                for (int j = start; j < end; j++) {
                    int boneId = deformBoneIndices.getInt(Mesh.byteIndex(j));
                    float weight = weights.getFloat(Mesh.byteIndex(deformWeightIndices.getInt(Mesh.byteIndex(j))));

                    if (weight <= 0.0F) continue;

                    positionTotal.add(poses[boneId].transform(position, scratch).mul(weight));

                    poses[boneId].transform(normal, scratch).mul(weight);
                    normalTotal.add(scratch.x(), scratch.y(), scratch.z());
                }

                positionTotal.mul(poseMatrix);

                // A vertex no bone claims accumulates nothing, and normalizing that is a NaN
                if (normalTotal.lengthSquared() > 0.0F) {
                    normalTotal.mul(normalMatrix).normalize();
                }

                drawingFunction.draw(
                    bufferBuilder,
                    positionTotal.x(),
                    positionTotal.y(),
                    positionTotal.z(),
                    normalTotal.x(),
                    normalTotal.y(),
                    normalTotal.z(),
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

        public int vertexCount() {
            return vertexCount;
        }

        public SkeletalMeshPartBufferView getBufferView() {
            return new SkeletalMeshPartBufferView(drawIndices.nioBuffer().asFloatBuffer().asReadOnlyBuffer());
        }
    }

    public static SkeletalMeshBuilder builder() {
        return new SkeletalMeshBuilder();
    }

    public static class SkeletalMeshBuilder extends CommonMeshBuilder<SkeletalMeshPart> {
        public float[] weights;
        public int[] deformationFromToArray;
        public int[] affectingBoneIdArray;
        public int[] affectingWeightIndexArray;

        /// An array of actual weight value (referenced by [#deformWeightIndices])
        public SkeletalMeshBuilder weights(final float[] weights) {
            this.weights = weights;
            return this;
        }

        /// Array of indices
        public SkeletalMeshBuilder deformationFromToArray(final int[] deformationFromToArray) {
            this.deformationFromToArray = deformationFromToArray;
            return this;
        }

        /// Array of boneIDs affecting a vertex
        public SkeletalMeshBuilder affectingBoneIdArray(final int[] affectingBoneIdArray) {
            this.affectingBoneIdArray = affectingBoneIdArray;
            return this;
        }

        /// Array of weight indices affecting a vertex
        public SkeletalMeshBuilder affectingWeightIndexArray(final int[] affectingWeightIndexArray) {
            this.affectingWeightIndexArray = affectingWeightIndexArray;
            return this;
        }
    }

    /// Returns a "Buffer lookup" in readonly.
    public SkeletalMeshVertexBufferView getBufferView() {
        return new SkeletalMeshVertexBufferView(
            positions.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            normals.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            uvs.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            weights.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            deformationFromToIndices.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            deformBoneIndices.nioBuffer().asFloatBuffer().asReadOnlyBuffer(),
            deformWeightIndices.nioBuffer().asFloatBuffer().asReadOnlyBuffer()
        );
    }

    public record SkeletalMeshVertexBufferView(
        FloatBuffer positions,
        FloatBuffer normals,
        FloatBuffer uvs,
        FloatBuffer weights,
        FloatBuffer deformationFromToIndices,
        FloatBuffer deformBoneIndices,
        FloatBuffer deformWeightIndices
    ) {}

    public record SkeletalMeshPartBufferView(FloatBuffer drawIndices) {
    }
}
