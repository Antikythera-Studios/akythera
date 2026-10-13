package com.yesman.akythera.client.core.asset.loader.binary;

import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.physics.ClothPhysicsData;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/// Reads the cloth a skeletal mesh carries: `animmodels/cloth_physics/<mesh path>.akasset` for the
/// weights and offset pairs, and its `.akmeta` sidecar for [ClothPhysicsData.Metadata].
public final class ClothPhysicsLoader implements BinaryAssetLoader {
    public static final String DIRECTORY = "animmodels/cloth_physics/";

    private final Identifier resourcePath;

    private ClothPhysicsLoader(final Identifier resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public Identifier resourcePath() {
        return resourcePath;
    }

    /// The cloth asset id of the mesh at `meshPath`, or null when `meshPath` is not under
    /// [MeshManager#DIRECTORY].
    public static @Nullable Identifier clothPathOf(final Identifier meshPath) {
        if (!meshPath.getPath().startsWith(MeshManager.DIRECTORY)) return null;

        return Identifier.fromNamespaceAndPath(
            meshPath.getNamespace(),
            DIRECTORY + meshPath.getPath().substring(MeshManager.DIRECTORY.length())
        );
    }

    /// The cloth of the mesh at `meshPath` (given without extension), or null when it has none.
    ///
    /// @exception ResourceLoadingFailureException when the cloth file exists but cannot be read
    public static @Nullable ClothPhysicsData read(final Identifier meshPath, final ResourceManager resourceManager)
        throws ResourceLoadingFailureException
    {
        Identifier clothPath = clothPathOf(meshPath);

        if (clothPath == null) return null;

        Identifier asBinary = Identifier.fromNamespaceAndPath(clothPath.getNamespace(), clothPath.getPath() + "." + AssetLoader.BINARY_EXTENSION);
        Optional<Resource> resource = resourceManager.getResource(asBinary);

        if (resource.isEmpty()) return null;

        ClothPhysicsData.Metadata metadata = AssetLoader.readMetadata(
            clothPath, resourceManager, ClothPhysicsData.Metadata.CODEC, ClothPhysicsData.Metadata.DEFAULT
        );
        ClothPhysicsLoader loader = new ClothPhysicsLoader(asBinary);

        try (InputStream inputStream = resource.get().open()) {
            byte[] decompressed = loader.decompress(inputStream.readAllBytes());
            float[] weights = null;
            int[] offsetPairs = new int[0];

            for (PayloadBlock payloadBlock : loader.blocksLookup(decompressed)) {
                if (payloadBlock.dataType() == DataType.CLOTH_WEIGHTS) {
                    weights = BinaryAssetLoader.readFloatArray(decompressed, payloadBlock.bodyOffset(), payloadBlock.payloadSize() / 4);
                } else if (payloadBlock.dataType() == DataType.CLOTH_OFFSET_PAIRS) {
                    offsetPairs = BinaryAssetLoader.readIntArray(decompressed, payloadBlock.bodyOffset(), payloadBlock.payloadSize() / 4);
                }
            }

            if (weights == null) {
                throw new ResourceLoadingFailureException(asBinary, "Missing cloth weights.");
            }

            return new ClothPhysicsData(metadata.armaturePhysics(), metadata.parameters(), weights, offsetPairs);
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(asBinary, e);
        }
    }
}
