package com.yesman.akythera.core.asset.loader;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.binary.BinaryArmatureLoader;
import com.yesman.akythera.core.asset.loader.json.JsonArmatureLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;
import java.util.Optional;

/// Loads common side assets, [Armature], [AnimationClip], and [Bone]
public interface ArmatureLoader extends AssetLoader {
    /// Globally applied root bone name
    String ROOT_BONE = "root";

    /// Loads an armature with type constructor
    <T extends Armature> T loadArmature(ArmatureConstructor<T> armatureConstructor) throws ResourceLoadingFailureException;

    /// Returns a matching asset loader depending on the file extension (`.akasset` or `.json`)
    ///
    /// If both binary and json files exist, binary file has higher priority to load
    static ArmatureLoader getLoader(Identifier resourcePath, ResourceManager resourceManager)
        throws ResourceLoadingFailureException
    {
        Identifier asBinary = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + "." + AssetLoader.BINARY_EXTENSION);
        Optional<Resource> binaryResource = resourceManager.getResource(asBinary);

        if (binaryResource.isPresent()) {
            try {
                return new BinaryArmatureLoader(asBinary, binaryResource.get());
            } catch (ResourceLoadingFailureException e) {
                Akythera.LOGGER.warn("Failed at loading a binary file. Now trying to read a json file...", e);
            }
        }

        Identifier asJson = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".json");
        Optional<Resource> jsonResource = resourceManager.getResource(asJson);

        if (jsonResource.isPresent()) {
            return new JsonArmatureLoader(asJson, jsonResource.get());
        }

        throw new ResourceLoadingFailureException(resourcePath, "Found neither binary(.akasset) nor json(.json) asset file");
    }

    /// Typed armature constructors to return typed objects
    @FunctionalInterface
    interface ArmatureConstructor<T extends Armature> {
        T construct(Bone rootBone, int boneCount, Map<String, Bone> bonesByName);
    }
}
