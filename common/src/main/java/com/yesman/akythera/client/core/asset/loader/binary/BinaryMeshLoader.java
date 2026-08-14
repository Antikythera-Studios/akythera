package com.yesman.akythera.client.core.asset.loader.binary;

import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/// Load meshes from binary files. Instances are dedicated for loading a resource specified by [#resourcePath].
public final class BinaryMeshLoader implements MeshLoader, BinaryAssetLoader {
    private final Identifier resourcePath;
    private final Resource resource;
    private final Metadata metadata;

    public BinaryMeshLoader(Identifier resourcePath, Resource resource, Metadata metadata)
        throws ResourceLoadingFailureException
    {
        this.resourcePath = resourcePath;
        this.resource = resource;
        this.metadata = metadata;
    }

    @Override
    public Identifier resourcePath() {
        return resourcePath;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public <T extends StaticMesh> T loadStaticMesh(MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor)
        throws ResourceLoadingFailureException
    {
        try (InputStream inputStream = resource.open()) {
            byte[] decompressed = decompress(inputStream.readAllBytes());
            var payloadBlocks = blocksLookup(decompressed);

            if (!checkMeshBlocks(payloadBlocks, false)) {
                throw new ResourceLoadingFailureException(resourcePath(), "Missing static mesh data.");
            }

            Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart> staticMeshBuilder = StaticMesh.builder();

            for (PayloadBlock payloadBlock : payloadBlocks) {
                switch (payloadBlock.dataType()) {
                    case MESH_POSITIONS -> {
                        float[] positions = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        convertZUpVectorArrayIntoYUpArray(positions);
                        staticMeshBuilder.positions(positions);
                    }
                    case MESH_UVS -> {
                        float[] uvs = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        staticMeshBuilder.normals(uvs);
                    }
                    case MESH_NORMALS -> {
                        float[] normals = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        convertZUpVectorArrayIntoYUpArray(normals);
                        staticMeshBuilder.normals(normals);
                    }
                    case MESH_SUBMESH -> {
                        String partName = BinaryAssetLoader.readString(decompressed, payloadBlock.offset());
                        int arrayOffset = 4 + partName.length();

                        // Starts from string offset
                        int[] indices =  BinaryAssetLoader.readIntArray(
                            decompressed,
                            payloadBlock.bodyOffset() + arrayOffset,
                            (payloadBlock.payloadSize() - arrayOffset) / 4
                        );

                        staticMeshBuilder.addPart(partName, VertexBuilder.create(indices));
                    }
                }
            }

            staticMeshBuilder.metadata(metadata);

            return meshConstructor.construct(staticMeshBuilder);
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    @Override
    public <T extends SkeletalMesh> T loadSkeletalMesh(MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor)
        throws ResourceLoadingFailureException
    {
        try (InputStream inputStream = resource.open()) {
            byte[] decompressed = decompress(inputStream.readAllBytes());
            var payloadBlocks = blocksLookup(decompressed);

            if (!checkMeshBlocks(payloadBlocks, true)) {
                throw new ResourceLoadingFailureException(resourcePath(), "Missing skeletal mesh data.");
            }

            int[] boneCounts = null;
            int[] boneWeightIndices = null;
            SkeletalMesh.SkeletalMeshBuilder skeletalMeshBuilder = SkeletalMesh.builder();

            for (PayloadBlock payloadBlock : payloadBlocks) {
                switch (payloadBlock.dataType()) {
                    case MESH_POSITIONS -> {
                        float[] positions = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        convertZUpVectorArrayIntoYUpArray(positions);
                        skeletalMeshBuilder.positions(positions);
                    }
                    case MESH_UVS -> {
                        float[] uvs = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        skeletalMeshBuilder.uvs(uvs);
                    }
                    case MESH_NORMALS -> {
                        float[] normals = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        convertZUpVectorArrayIntoYUpArray(normals);
                        skeletalMeshBuilder.normals(normals);
                    }
                    case MESH_BONE_COUNTS -> {
                        boneCounts = BinaryAssetLoader.readIntArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                    }
                    case MESH_BONE_WEIGHT_INDICES -> {
                        boneWeightIndices = BinaryAssetLoader.readIntArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                    }
                    case MESH_WEIGHTS -> {
                        float[] weights = BinaryAssetLoader.readFloatArray(
                            decompressed,
                            payloadBlock.bodyOffset(),
                            payloadBlock.payloadSize() / 4
                        );
                        skeletalMeshBuilder.weights(weights);
                    }
                    case MESH_SUBMESH -> {
                        String partName = BinaryAssetLoader.readString(decompressed, payloadBlock.offset() + BLOCK_HEADER_SIZE);
                        int arrayOffset = 4 + partName.length();

                        // Starts from string offset
                        int[] indices = BinaryAssetLoader.readIntArray(
                            decompressed,
                            payloadBlock.bodyOffset() + arrayOffset,
                            (payloadBlock.payloadSize() - arrayOffset) / 4
                        );

                        skeletalMeshBuilder.addPart(partName, VertexBuilder.create(indices));
                    }
                }
            }

            int[] affectingBoneIdArray = new int[boneWeightIndices.length/2];
            int[] affectingWeightIndexArray = new int[boneWeightIndices.length/2];
            int[] deformationFromToArray = new int[boneCounts.length*2];

            for (int i = 0; i < affectingBoneIdArray.length; i++) {
                affectingBoneIdArray[i] = boneWeightIndices[i*2];
                affectingWeightIndexArray[i] = boneWeightIndices[i*2 + 1];
            }

            int from = 0;

            for (int i = 0; i < boneCounts.length; i++) {
                deformationFromToArray[i*2] = from;
                deformationFromToArray[i*2 + 1] = from + boneCounts[i];
                from += boneCounts[i];
            }

            skeletalMeshBuilder
                .affectingBoneIdArray(affectingBoneIdArray)
                .affectingWeightIndexArray(affectingWeightIndexArray)
                .deformationFromToArray(deformationFromToArray)
            ;

            skeletalMeshBuilder.metadata(metadata);

            return meshConstructor.construct(skeletalMeshBuilder);
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    /// Check for data blocks required by loading a specific mesh
    private static boolean checkMeshBlocks(final List<PayloadBlock> payloadBlocks, final boolean skeletalMesh) {
        // Common mesh blocks for static & skeletal
        boolean hasPositions = false;
        boolean hasUvs = false;
        boolean hasNormals = false;
        boolean hasPartitions = false;

        // Skeletal mesh blocks
        boolean hasBoneCounts = false;
        boolean hasBoneWeightIndices = false;
        boolean hasWeights = false;

        for (PayloadBlock payloadBlock : payloadBlocks) {
            hasPositions |= payloadBlock.dataType() == DataType.MESH_POSITIONS;
            hasUvs |= payloadBlock.dataType() == DataType.MESH_UVS;
            hasNormals |= payloadBlock.dataType() == DataType.MESH_NORMALS;
            hasPartitions |= payloadBlock.dataType() == DataType.MESH_SUBMESH;
            hasBoneCounts |= payloadBlock.dataType() == DataType.MESH_BONE_COUNTS;
            hasBoneWeightIndices |= payloadBlock.dataType() == DataType.MESH_BONE_WEIGHT_INDICES;
            hasWeights |= payloadBlock.dataType() == DataType.MESH_WEIGHTS;
        }

        return hasPositions &&
            hasUvs &&
            hasNormals &&
            hasPartitions &&
            !skeletalMesh ||
            (
                hasBoneCounts &&
                hasBoneWeightIndices &&
                hasWeights
            );
    }

    /// Convert Blender space vector array into Minecraft's Y-Up coord vector list
    private static void convertZUpVectorArrayIntoYUpArray(float[] vectorArray) {
        for (int i = 0; i < vectorArray.length / 3; i++) {
            int k = i * 3;
            Vector3f vector = new Vector3f(
                vectorArray[k],
                vectorArray[k+1],
                vectorArray[k+2]
            );

            // Convert from blender space to GL coord system
            vector.mul(BLENDER_TO_MINECRAFT_COORD_SYSTEM_3F);

            vectorArray[k] = vector.x;
            vectorArray[k+1] = vector.y;
            vectorArray[k+2] = vector.z;
        }
    }
}
