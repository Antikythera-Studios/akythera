package com.yesman.akythera.core.asset.loader;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

/// Read asset files in the resource pool, returns asset objects
/// that are actually used in game.
///
/// Avoid manual asset loads. Use [AssetAccessor] which forward
/// declares an assets and load the asset object in the first
/// reference.
public interface AssetLoader {
    /// Coord system converter as Blender has Z-Up system, and Minecraft has Y-Up basis.
    Matrix4f BLENDER_TO_MINECRAFT_COORD_SYSTEM = new Matrix4f().rotate((float)Math.toRadians(-90.0F), 1.0F, 0.0F, 0.0F);

    /// File extension of assets as binary files.
    String BINARY_EXTENSION = "akasset";

    /// File extension of an asset's sidecar metadata. The content is `.json`.
    String METADATA_EXTENSION = ".akmeta";

    /// A resource path identifier
    Identifier resourcePath();

    /// The sidecar metadata id of an asset path, with or without its `.akasset` / `.json` extension.
    static Identifier metadataPath(final Identifier assetPath) {
        return Identifier.fromNamespaceAndPath(
            assetPath.getNamespace(),
            ParseUtils.trimExtensions(assetPath.getPath(), BINARY_EXTENSION, "json") + METADATA_EXTENSION
        );
    }

    /// Decodes an asset's sidecar metadata, falling back to `orElse` when the file is absent,
    /// unreadable, or malformed. Metadata never fails a load — an asset without it is simply
    /// read with the defaults.
    static <T> T readMetadata(
        final Identifier assetPath,
        final ResourceManager resourceManager,
        final Codec<T> codec,
        final T orElse
    ) {
        Identifier metadataPath = metadataPath(assetPath);
        Optional<Resource> resource = resourceManager.getResource(metadataPath);

        if (resource.isEmpty()) {
            return orElse;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                .resultOrPartial(error -> Akythera.LOGGER.error("Failed at decoding asset metadata {}: {}", metadataPath, error))
                .orElse(orElse);
        } catch (IOException | JsonParseException e) {
            Akythera.LOGGER.error("Failed at reading asset metadata {}", metadataPath, e);
            return orElse;
        }
    }

    /// Decodes the `.json` asset at `assetPath` (given without extension).
    ///
    /// @exception ResourceLoadingFailureException when the file is absent, unreadable, or does not
    ///                                            decode against `codec`
    static <T> T readJsonAsset(
        final Identifier assetPath,
        final ResourceManager resourceManager,
        final Codec<T> codec
    ) throws ResourceLoadingFailureException {
        Identifier asJson = Identifier.fromNamespaceAndPath(assetPath.getNamespace(), assetPath.getPath() + ".json");
        Resource resource = resourceManager.getResource(asJson)
            .orElseThrow(() -> new ResourceLoadingFailureException(assetPath, "Found no json(.json) asset file"));

        try (BufferedReader reader = resource.openAsReader()) {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                .getOrThrow(message -> new ResourceLoadingFailureException(assetPath, message));
        } catch (IOException | JsonParseException e) {
            throw new ResourceLoadingFailureException(assetPath, e);
        }
    }

    /// Reads an asset's sidecar metadata. [AssetLoader#readMetadata] is the shape a loader uses when
    /// its caller names none.
    ///
    /// `codec` is the one the asset's own loader would have read the file with; an implementation
    /// whose fields must decode against something other than the game's packs substitutes its own.
    @FunctionalInterface
    interface MetadataLoader<T> {
        T load(final Identifier assetPath, final ResourceManager resourceManager, final Codec<T> codec, final T orElse);
    }
}
