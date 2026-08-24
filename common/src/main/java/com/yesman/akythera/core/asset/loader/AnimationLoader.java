package com.yesman.akythera.core.asset.loader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.binary.BinaryAnimationLoader;
import com.yesman.akythera.core.asset.loader.json.JsonAnimationLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Loads common side animation asset, a [AnimationClip]
public interface AnimationLoader extends AssetLoader {
    /// Loads keyframes & animation transforms as authored in the asset file, before [Metadata]
    /// is applied. Since [AnimationClip] has some metadata declared in codebase, this method is
    /// not implemented as directly returning it.
    ///
    /// @param armature is nullable for binary assets, but non-null for json assets
    AnimationClip loadRawAnimationClip(AssetAccessor<? extends Armature> armature) throws ResourceLoadingFailureException;

    /// The sidecar metadata of the asset this loader reads.
    Metadata metadata();

    /// Loads the clip and applies [Metadata#importType].
    ///
    /// @param armature is nullable for binary assets, but non-null for json assets
    default AnimationClip loadAnimationClip(AssetAccessor<? extends Armature> armature) throws ResourceLoadingFailureException {
        AnimationClip clip = loadRawAnimationClip(armature);

        return metadata().importType() == ImportType.ADDITIVE ? toAdditive(clip) : clip;
    }

    /// Rewrites every keyframe as the transform relative to the reference pose named by [#metadata].
    ///
    /// @see Transform#relativeTo
    private AnimationClip toAdditive(final AnimationClip source) throws ResourceLoadingFailureException {
        AssetAccessor<AnimationClip> refPoseProvider = metadata().refPoseProvider();

        if (refPoseProvider == null) {
            throw new ResourceLoadingFailureException(
                resourcePath(), "An additive animation needs 'ref_pose_provider' in its metadata.");
        }

        AnimationClip refPoseClip = refPoseProvider.get();

        if (refPoseClip == null) {
            throw new ResourceLoadingFailureException(
                resourcePath(), "Reference pose animation " + refPoseProvider.identifier() + " could not be read.");
        }

        Pose refPose = refPoseClip.getPose(metadata().refPoseTimeStamp());
        AnimationClip.Builder builder = AnimationClip.builder();

        source.visitBoneTracks((boneName, track) -> {
            Transform base = refPose.orElseIdentity(boneName);
            List<Keyframe> keyframes = new ArrayList<>(track.length());

            track.forEach((_, keyframe) ->
                keyframes.add(new Keyframe(keyframe.timeStamp(), keyframe.transform().relativeTo(base)))
            );

            builder.addBoneAnimation(boneName, new BoneAnimationTrack(keyframes));
        });

        return builder.build();
    }

    /// Returns a matching asset loader depending on the file extension (`.akasset` or `.json`)
    ///
    /// If both binary and json files exist, binary file has higher priority to load (beware no fallback load for .json)
    ///
    /// `metadataLoader` reads the asset's sidecar. A caller that resolves [Metadata#refPoseProvider]
    /// outside the game's packs supplies its own; [AssetLoader#readMetadata] is the plain read.
    static AnimationLoader getLoader(
        Identifier resourcePath,
        ResourceManager resourceManager,
        AssetLoader.MetadataLoader<Metadata> metadataLoader
    ) throws ResourceLoadingFailureException {
        Metadata metadata = metadataLoader.load(resourcePath, resourceManager, Metadata.CODEC, Metadata.DEFAULT);
        Identifier asBinary = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + "." + AssetLoader.BINARY_EXTENSION);
        Optional<Resource> binaryResource = resourceManager.getResource(asBinary);

        if (binaryResource.isPresent()) {
            try {
                return new BinaryAnimationLoader(asBinary, binaryResource.get(), metadata);
            } catch (ResourceLoadingFailureException e) {
                Akythera.LOGGER.warn("Failed at loading a binary file. Now trying to read a json file...", e);
            }
        }

        Identifier asJson = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".json");
        Optional<Resource> jsonResource = resourceManager.getResource(asJson);

        if (jsonResource.isPresent()) {
            return new JsonAnimationLoader(asJson, jsonResource.get(), metadata);
        }

        throw new ResourceLoadingFailureException(resourcePath, "Found neither binary(.akasset) nor json(.json) asset file");
    }

    record Metadata(ImportType importType, @Nullable AssetAccessor<AnimationClip> refPoseProvider, float refPoseTimeStamp) {
        /// The fields as they sit at the root of a sidecar, so a reader that carries fields of its
        /// own can place them alongside instead of under a section.
        public static final MapCodec<Metadata> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ImportType.CODEC.fieldOf("import_type").forGetter(Metadata::importType),
                    AnimationManager.CODEC.optionalFieldOf("ref_pose_provider").forGetter(ParseUtils.provideOptional(Metadata::refPoseProvider)),
                    Codec.FLOAT.fieldOf("ref_pose_timestamp").forGetter(Metadata::refPoseTimeStamp)
                ).apply(instance, Metadata::new)
        );

        public static final Codec<Metadata> CODEC = MAP_CODEC.codec();

        /// What an asset without a sidecar file is read with.
        public static final Metadata DEFAULT = new Metadata(ImportType.COMMON, (AssetAccessor<AnimationClip>)null, 0.0F);

        // Serialization
        public Metadata(final ImportType importType, final Optional<AssetAccessor<AnimationClip>> refPoseProvider, final float refPoseTimeStamp) {
            this(importType, refPoseProvider.orElse(null), refPoseTimeStamp);
        }
    }

    enum ImportType implements StringRepresentable {
        /// Read the animation raw data as is
        COMMON("common"),

        /// Read the animation data as a *relative* transform from reference pose,
        /// specified in [Metadata#refPoseProvider], [Metadata#refPoseTimeStamp]
        ADDITIVE("additive");

        public static final Codec<ImportType> CODEC = StringRepresentable.fromEnum(ImportType::values);

        private final String name;

        ImportType(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
