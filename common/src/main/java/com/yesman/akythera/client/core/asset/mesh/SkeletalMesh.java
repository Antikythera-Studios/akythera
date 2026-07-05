package com.yesman.akythera.client.core.asset.mesh;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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

    public SkeletalMesh(SkeletalMeshBuilder meshBuilder) {
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
            meshPartBuilder.put(entry.getKey(), new SkeletalMeshPart(entry.getValue()));
        }

        meshParts = meshPartBuilder.build();
    }

    /// Draw a mesh with mesh deformation, by the bone transform in a specific pose.
    ///
    /// Requires **fully** bound transform matrices, by [Pose#getForwardBoundTransforms] x [Armature#boundBackwardTransform]
    /// 
    /// @see Pose#getForwardBoundTransforms the definition of forward binding
    /// @see Armature#boundBackwardTransform the definition of backward binding
    public void drawPosed(
        PoseStack poseStack,
        VertexConsumer bufferBuilder,
        DrawingFunction drawingFunction,
        int packedLight,
        float r,
        float g,
        float b,
        float a,
        int overlay,
        Matrix4f[] poses
    ) {
        for (SkeletalMeshPart part : getParts().values()) {
            if (!part.visible()) part.drawPosed(poseStack, bufferBuilder, drawingFunction, packedLight, r, g, b, a, overlay, poses);
        }
    }

    @Override
    public Map<String, SkeletalMeshPart> getParts() {
        return meshParts;
    }

    @Override
    public void destroy() {
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
    }

    public class SkeletalMeshPart implements MeshPart {
        /// A buffer that (position, normal, uv) are in 1D array
        private final ByteBuf drawIndices;
        private final int vertexCount;
        private boolean visible;

        private SkeletalMeshPart(List<VertexBuilder> vertexBuilders) {
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

        private static final Matrix4f[] POSE_MATRIX_BUFFER = new Matrix4f[1024];
        private static final Matrix4f[] NORMAL_MATRIX_BUFFER = new Matrix4f[1024];

        static {
            for (int i = 0; i < 1024; i++) {
                POSE_MATRIX_BUFFER[i] = new Matrix4f();
                NORMAL_MATRIX_BUFFER[i] = new Matrix4f();
            }
        }

        public void drawPosed(
            PoseStack poseStack,
            VertexConsumer bufferBuilder,
            DrawingFunction drawingFunction,
            int packedLight,
            float r,
            float g,
            float b,
            float a,
            int overlay,
            Matrix4f[] poses
        ) {
            Matrix4f poseMatrix = poseStack.last().pose();
            Matrix3f normalMatrix = poseStack.last().normal();

            // Calculate poses, normals
            for (int i = 0; i < poses.length; i++) {
                POSE_MATRIX_BUFFER[i].set(poses[i]);

                NORMAL_MATRIX_BUFFER[i] = POSE_MATRIX_BUFFER[i];
                NORMAL_MATRIX_BUFFER[i].translate(
                    -NORMAL_MATRIX_BUFFER[i].m30(),
                    -NORMAL_MATRIX_BUFFER[i].m31(),
                    -NORMAL_MATRIX_BUFFER[i].m32()
                );
            }

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
                Vector4f normal = new Vector4f(
                    normals.getFloat(normalIdx*3),
                    normals.getFloat(normalIdx*3+1),
                    normals.getFloat(normalIdx*3+2),
                    1.0F
                );
                Vector4f positionTotal = new Vector4f();
                Vector3f normalTotal = new Vector3f();

                int start = deformationFromToIndices.getInt(positionIdx * 2);
                int end = deformationFromToIndices.getInt(positionIdx * 2 + 1);

                // Apply transforms of affecting bones
                for (int j = start; j < end; j++) {
                    int boneId = deformBoneIndices.getInt(j);
                    float weight = weights.getInt(deformWeightIndices.getInt(j));
                    position.add(position.mul(poses[boneId], new Vector4f()).mul(weight));
                    Vector4f normalIncrement = normal.mul(poses[boneId], new Vector4f()).mul(weight);
                    normalTotal.add(normalIncrement.x(), normalIncrement.y(), normalIncrement.z());
                }

                positionTotal.mul(poseMatrix);
                normalTotal.mul(normalMatrix);

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

    public static SkeletalMeshBuilder builder() {
        return new SkeletalMeshBuilder();
    }

    public static class SkeletalMeshBuilder extends CommonMeshBuilder<SkeletalMeshPart> {
        float[] weights;
        int[] deformationFromToArray;
        int[] affectingBoneIdArray;
        int[] affectingWeightIndexArray;

        /// An array of actual weight value (referenced by [#deformWeightIndices])
        public SkeletalMeshBuilder weights(float[] weights) {
            this.weights = weights;
            return this;
        }

        /// Array of indices
        public SkeletalMeshBuilder deformationFromToArray(int[] deformationFromToArray) {
            this.deformationFromToArray = deformationFromToArray;
            return this;
        }

        /// Array of boneIDs affecting a vertex
        public SkeletalMeshBuilder affectingBoneIdArray(int[] affectingBoneIdArray) {
            this.affectingBoneIdArray = affectingBoneIdArray;
            return this;
        }

        /// Array of weight indices affecting a vertex
        public SkeletalMeshBuilder affectingWeightIndexArray(int[] affectingWeightIndexArray) {
            this.affectingWeightIndexArray = affectingWeightIndexArray;
            return this;
        }
    }
}
