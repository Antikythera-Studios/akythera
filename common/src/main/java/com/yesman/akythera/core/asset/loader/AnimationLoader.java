package com.yesman.akythera.core.asset.loader;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.binary.BinaryAnimationLoader;
import com.yesman.akythera.core.asset.loader.json.JsonAnimationLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Optional;

/// Loads common side animation asset, a [AnimationClip]
public interface AnimationLoader extends AssetLoader {
    /// Loads keyframes & animation transforms to [AnimationClip]. Since [AnimationClip] has some
    /// metadata declared in codebase, this method is not implemented as directly returning it.
    ///
    /// @param armature is nullable for binary assets, but non-null for json assets
    AnimationClip loadAnimationClip(AssetAccessor<? extends Armature> armature) throws ResourceLoadingFailureException;

    /// Returns a matching asset loader depending on the file extension (`.efasset` or `.json`)
    ///
    /// If both binary and json files exist, binary file has higher priority to load (beware no fallback load for .json)
    static AnimationLoader getLoader(Identifier resourcePath, ResourceManager resourceManager)
        throws ResourceLoadingFailureException
    {
        Identifier asBinary = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".efasset");
        Optional<Resource> binaryResource = resourceManager.getResource(asBinary);

        if (binaryResource.isPresent()) {
            try {
                return new BinaryAnimationLoader(asBinary, binaryResource.get());
            } catch (ResourceLoadingFailureException e) {
                Akythera.LOGGER.warn("Failed at loading a binary file. Now trying to read a json file...", e);
            }
        }

        Identifier asJson = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".json");
        Optional<Resource> jsonResource = resourceManager.getResource(asJson);

        if (jsonResource.isPresent()) {
            return new JsonAnimationLoader(asJson, jsonResource.get());
        }

        throw new ResourceLoadingFailureException(resourcePath, "Found neither binary(.efasset) nor json(.json) asset file");
    }
}
