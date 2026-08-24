package com.yesman.akythera.client.core.asset.loader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.core.asset.loader.binary.BinaryMeshLoader;
import com.yesman.akythera.client.core.asset.loader.json.JsonMeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix3f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Loads client-side only assets, the [Mesh]
public interface MeshLoader extends AssetLoader {
    /// Coord system converter as Blender has Z-Up system, and Minecraft has Y-Up basis.
    Matrix3f BLENDER_TO_MINECRAFT_COORD_SYSTEM_3F = new Matrix3f().rotate((float)Math.toRadians(-90.0F), 1.0F, 0.0F, 0.0F);

    /// For old .json mesh files, where vertices are not partitioned.
    String UNGROUPED_PART_NAME = "ungrouped";

    /// The sidecar metadata of the asset this loader reads.
    Metadata metadata();

    /// Loads a static mesh. Even tho the asset file was exported
    /// as skeletal, this will extract only information to draw the mesh.
    ///
    /// @exception ResourceLoadingFailureException when asset file doesn't exist, invalid formats, or others
    <T extends StaticMesh> T loadStaticMesh(MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor) throws ResourceLoadingFailureException;

    /// Loads a skeletal mesh. If the asset file was exported as
    /// static, the program will throw an exception
    ///
    /// @exception ResourceLoadingFailureException when asset file doesn't exist, invalid formats, or others
    <T extends SkeletalMesh> T loadSkeletalMesh(MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor) throws ResourceLoadingFailureException;

    /// Returns a matching asset loader depending on the file extension (`.akasset` or `.json`)
    ///
    /// If both binary and json files exist, binary file has higher priority to load
    ///
    /// `metadataLoader` reads the asset's sidecar; [AssetLoader#readMetadata] is the plain read.
    static MeshLoader getLoader(
        Identifier resourcePath,
        ResourceManager resourceManager,
        AssetLoader.MetadataLoader<Metadata> metadataLoader
    ) throws ResourceLoadingFailureException {
        Metadata metadata = metadataLoader.load(resourcePath, resourceManager, Metadata.CODEC, Metadata.DEFAULT);
        Identifier asBinary = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + "." + AssetLoader.BINARY_EXTENSION);
        Optional<Resource> binaryResource = resourceManager.getResource(asBinary);

        if (binaryResource.isPresent()) {
            try {
                return new BinaryMeshLoader(asBinary, binaryResource.get(), metadata);
            } catch (ResourceLoadingFailureException e) {
                Akythera.LOGGER.warn("Failed at loading a binary file. Now trying to read a json file...", e);
            }
        }

        Identifier asJson = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".json");
        Optional<Resource> jsonResource = resourceManager.getResource(asJson);

        if (jsonResource.isPresent()) {
            return new JsonMeshLoader(asJson, jsonResource.get(), metadata);
        }

        throw new ResourceLoadingFailureException(resourcePath, "Found neither binary(.akasset) nor json(.json) asset file");
    }

    /// Typed mesh constructors to return typed objects
    @FunctionalInterface
    interface MeshConstructor<T extends Mesh<?>, B extends Mesh.CommonMeshBuilder<?>> {
        T construct(B meshBuilder);
    }

    /// The mesh's authored preview data: the `width` × `height` footprint, in blocks, that frames a
    /// model previewer's camera, and the texture that previewer binds.
    ///
    /// Read by `Model3DViewer` alone. An in-game renderer takes its texture from the render type the
    /// entity renderer picked, and never consults this.
    record Metadata(float width, float height, @Nullable Identifier texture) {
        public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.FLOAT.fieldOf("width").forGetter(Metadata::width),
                    Codec.FLOAT.fieldOf("height").forGetter(Metadata::height),
                    Identifier.CODEC.optionalFieldOf("texture").forGetter(ParseUtils.provideOptional(Metadata::texture))
                ).apply(instance, Metadata::new)
        );

        /// What a mesh without a sidecar file is read with — player-sized, untextured.
        public static final Metadata DEFAULT = new Metadata(0.6F, 1.8F, (Identifier) null);

        // Serialization
        public Metadata(final float width, final float height, final Optional<Identifier> texture) {
            this(width, height, texture.orElse(null));
        }
    }
}
