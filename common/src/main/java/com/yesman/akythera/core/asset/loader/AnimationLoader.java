package com.yesman.akythera.core.asset.loader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.ik.IKCurves;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.binary.BinaryAnimationLoader;
import com.yesman.akythera.core.asset.loader.json.JsonAnimationLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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

    /// Loads the clip and applies [Metadata#importType] and [Metadata#ik].
    ///
    /// @param armature is nullable for binary assets, but non-null for json assets
    ///
    /// @throws ResourceLoadingFailureException when a mesh space additive or a mirrored animation names no [Metadata#vendor]
    default AnimationClip loadAnimationClip(AssetAccessor<? extends Armature> armature) throws ResourceLoadingFailureException {
        boolean meshSpace = metadata().importType() == ImportType.ADDITIVE && metadata().additiveSpace() == AdditiveSpace.MESH;

        if ((meshSpace || metadata().enableMirroring()) && !metadata().hasVendor()) {
            throw new ResourceLoadingFailureException(resourcePath(), "A mesh space additive or a mirrored animation needs a vendor armature.");
        }

        AnimationClip raw = loadRawAnimationClip(armature);
        AnimationClip clip = metadata().importType() == ImportType.ADDITIVE ? toAdditive(raw) : raw;
        clip.applyMetadata(metadata());

        return clip;
    }

    /// Rewrites every keyframe as the transform relative to the reference pose named by [#metadata], or this clip, in
    /// the [Metadata#additiveSpace].
    ///
    /// @see Transform#relativeTo
    private AnimationClip toAdditive(final AnimationClip source) throws ResourceLoadingFailureException {
        AssetAccessor<AnimationClip> refPoseProvider = metadata().refPoseProvider();
        AnimationClip refPoseClip = refPoseProvider == null ? source : refPoseProvider.get();

        if (refPoseClip == null) {
            throw new ResourceLoadingFailureException(
                resourcePath(), "Reference pose animation " + refPoseProvider.identifier() + " could not be read.");
        }

        Pose refPose = refPoseClip.getPose(metadata().refPoseTimeStamp());
        AnimationClip.Builder builder = AnimationClip.builder();

        if (metadata().additiveSpace() == AdditiveSpace.MESH) {
            Armature skeleton = metadata().vendor().get();

            if (skeleton == null) {
                throw new ResourceLoadingFailureException(resourcePath(), "Vendor armature " + metadata().vendor().identifier() + " could not be read.");
            }

            Map<String, Quaterniond> refRotations = PoseOperators.modelRotations(skeleton, refPose);
            Map<String, List<Keyframe>> keyframes = new HashMap<>();
            Set<Float> timeStamps = new TreeSet<>();

            source.visitBoneTracks((_, track) -> track.forEach((_, keyframe) -> timeStamps.add(keyframe.timeStamp())));

            for (float timeStamp : timeStamps) {
                PoseOperators.modelRotations(skeleton, source.getPose(timeStamp)).forEach((boneName, rotation) -> {
                    Quaterniond delta = rotation.mul(refRotations.get(boneName).invert(new Quaterniond()), new Quaterniond()).normalize();
                    keyframes.computeIfAbsent(boneName, _ -> new ArrayList<>()).add(new Keyframe(timeStamp, Transform.fromRotation(delta)));
                });
            }

            keyframes.forEach((boneName, track) -> builder.addBoneAnimation(boneName, new BoneAnimationTrack(track)));

            return builder.build();
        }

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

    /// @param vendor          the armature the clip was authored against: the skeleton of an [AdditiveSpace#MESH] bake and
    ///                        the bone pairs of a mirrored bake. Null when the sidecar names none.
    /// @param enableMirroring whether a mirrored twin is baked, see [AnimationManager#getMirrorAnimation]
    /// @param ik              the [IKCurves] of the IK chains the clip drives, by each chain's tip bone
    record Metadata(
        ImportType importType,
        @Nullable AssetAccessor<AnimationClip> refPoseProvider, float refPoseTimeStamp, AdditiveSpace additiveSpace, // Additive animation properties
        @Nullable AssetAccessor<? extends Armature> vendor, boolean enableMirroring,
        Map<String, IKCurves> ik
    ) {
        /// The fields as they sit at the root of a sidecar, so a reader that carries fields of its
        /// own can place them alongside instead of under a section.
        public static final MapCodec<Metadata> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ImportType.CODEC.fieldOf("import_type").forGetter(Metadata::importType),
                    AnimationManager.CODEC.optionalFieldOf("ref_pose_provider").forGetter(ParseUtils.provideOptional(Metadata::refPoseProvider)),
                    Codec.FLOAT.fieldOf("ref_pose_timestamp").forGetter(Metadata::refPoseTimeStamp),
                    AdditiveSpace.CODEC.optionalFieldOf("additive_space", AdditiveSpace.LOCAL).forGetter(Metadata::additiveSpace),
                    ArmatureManager.CODEC.optionalFieldOf("vendor").forGetter(ParseUtils.provideOptional(Metadata::vendor)),
                    Codec.BOOL.optionalFieldOf("enable_mirroring", false).forGetter(Metadata::enableMirroring),
                    Codec.unboundedMap(Codec.STRING, IKCurves.CODEC).optionalFieldOf("ik", Map.of()).forGetter(Metadata::ik)
                ).apply(instance, Metadata::new)
        );

        public static final Codec<Metadata> CODEC = MAP_CODEC.codec();

        /// What an asset without a sidecar file is read with.
        public static final Metadata DEFAULT =
            new Metadata(ImportType.COMMON, (AssetAccessor<AnimationClip>)null, 0.0F, AdditiveSpace.LOCAL, null, false, Map.of());

        // Serialization
        public Metadata(
            final ImportType importType, final Optional<AssetAccessor<AnimationClip>> refPoseProvider, final float refPoseTimeStamp,
            final AdditiveSpace additiveSpace, final Optional<AssetAccessor<? extends Armature>> vendor, final boolean enableMirroring,
            final Map<String, IKCurves> ik
        ) {
            this(importType, refPoseProvider.orElse(null), refPoseTimeStamp, additiveSpace, vendor.orElse(null), enableMirroring, ik);
        }

        /// Whether [#vendor] holds a real armature.
        public boolean hasVendor() {
            return vendor != null && vendor != ArmatureManager.EMPTY_ARMATURE_ACCESSOR;
        }

        /// Whether a mirrored twin is baked: [#enableMirroring] is on and [#hasVendor].
        public boolean isMirroring() {
            return enableMirroring && hasVendor();
        }
    }

    enum ImportType implements StringRepresentable {
        /// Read the animation raw data as is
        COMMON("common"),

        /// Read the animation data as a *relative* transform from reference pose,
        /// specified in [Metadata#refPoseProvider] (this animation when absent), [Metadata#refPoseTimeStamp]
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

    /// The space an [ImportType#ADDITIVE] animation's deltas are taken in.
    enum AdditiveSpace implements StringRepresentable {
        /// Each bone's transform relative to its own reference transform, laid on by [PoseOperators#additivePose]
        LOCAL("local"),

        /// Each bone's model rotation relative to its reference model rotation, translation dropped, laid on by
        /// [PoseOperators#meshSpaceAdditivePose]. Needs the armature at load.
        MESH("mesh");

        public static final Codec<AdditiveSpace> CODEC = StringRepresentable.fromEnum(AdditiveSpace::values);

        private final String name;

        AdditiveSpace(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
