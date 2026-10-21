package com.yesman.akythera.core.asset.loader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.binary.BinaryArmatureLoader;
import com.yesman.akythera.core.asset.loader.json.JsonArmatureLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Loads common side assets, [Armature], [AnimationClip], and [Bone]
public interface ArmatureLoader extends AssetLoader {
    /// Globally applied root bone name
    String ROOT_BONE = "root";

    /// How far up the tail of a root bone stands when the pipeline generates one, in armature units.
    float GENERATED_ROOT_TAIL_HEIGHT = 0.05F;

    /// Loads the bone hierarchy as authored in the asset file, before [Metadata] is applied.
    <T extends Armature> T loadRawArmature(ArmatureConstructor<T> armatureConstructor) throws ResourceLoadingFailureException;

    /// The sidecar metadata of the asset this loader reads.
    Metadata metadata();

    /// Loads the armature and installs [#metadata] on it.
    default <T extends Armature> T loadArmature(ArmatureConstructor<T> armatureConstructor) throws ResourceLoadingFailureException {
        T armature = loadRawArmature(armatureConstructor);
        armature.applyMetadata(metadata());

        return armature;
    }

    /// Returns a matching asset loader depending on the file extension (`.akasset` or `.json`)
    ///
    /// If both binary and json files exist, binary file has higher priority to load
    static ArmatureLoader getLoader(Identifier resourcePath, ResourceManager resourceManager)
        throws ResourceLoadingFailureException
    {
        Metadata metadata = AssetLoader.readMetadata(resourcePath, resourceManager, Metadata.CODEC, Metadata.DEFAULT);
        Identifier asBinary = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + "." + AssetLoader.BINARY_EXTENSION);
        Optional<Resource> binaryResource = resourceManager.getResource(asBinary);

        if (binaryResource.isPresent()) {
            try {
                return new BinaryArmatureLoader(asBinary, binaryResource.get(), metadata);
            } catch (ResourceLoadingFailureException e) {
                Akythera.LOGGER.warn("Failed at loading a binary file. Now trying to read a json file...", e);
            }
        }

        Identifier asJson = Identifier.fromNamespaceAndPath(resourcePath.getNamespace(), resourcePath.getPath() + ".json");
        Optional<Resource> jsonResource = resourceManager.getResource(asJson);

        if (jsonResource.isPresent()) {
            return new JsonArmatureLoader(asJson, jsonResource.get(), metadata);
        }

        throw new ResourceLoadingFailureException(resourcePath, "Found neither binary(.akasset) nor json(.json) asset file");
    }

    record Metadata(List<MirrorBonePair> mirrorBonePairs, List<JointConstraint> jointConstraints) {
        /// The fields as they sit at the root of a sidecar, so a reader that carries fields of its
        /// own can place them alongside instead of under a section.
        public static final MapCodec<Metadata> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    MirrorBonePair.CODEC.listOf().optionalFieldOf("mirror_bone_pairs", List.of())
                        .forGetter(Metadata::mirrorBonePairs),
                    JointConstraint.CODEC.listOf().optionalFieldOf("joint_constraints", List.of())
                        .forGetter(Metadata::jointConstraints)
                ).apply(instance, Metadata::new)
        );

        public static final Codec<Metadata> CODEC = MAP_CODEC.codec();

        /// What an asset without a sidecar file is read with.
        public static final Metadata DEFAULT = new Metadata(List.of(), List.of());

        /// [#mirrorBonePairs] as a lookup, each pair entered from both ends.
        public Map<String, String> mirrorBoneMap() {
            Map<String, String> map = new HashMap<>(mirrorBonePairs.size() * 2);

            for (MirrorBonePair pair : mirrorBonePairs) {
                map.put(pair.first(), pair.second());
                map.put(pair.second(), pair.first());
            }

            return Map.copyOf(map);
        }
    }

    /// Two bones standing on opposite sides of the mirror plane. A bone belongs to at most one pair.
    record MirrorBonePair(String first, String second) {
        public static final Codec<MirrorBonePair> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("first").forGetter(MirrorBonePair::first),
                    Codec.STRING.fieldOf("second").forGetter(MirrorBonePair::second)
                ).apply(instance, MirrorBonePair::new)
        );
    }

    record JointConstraint(String joint, String bend, float maxBend, float maxStretch) {
        public static final Codec<JointConstraint> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("joint").forGetter(JointConstraint::joint),
                    Codec.STRING.fieldOf("bend").forGetter(JointConstraint::bend),
                    Codec.floatRange(0.0F, 180.0F).optionalFieldOf("max_bend", 180.0F).forGetter(JointConstraint::maxBend),
                    Codec.floatRange(1.0F, Float.MAX_VALUE).optionalFieldOf("max_stretch", 1.0F).forGetter(JointConstraint::maxStretch)
                ).apply(instance, JointConstraint::new)
        );
    }

    /// Typed armature constructors to return typed objects
    @FunctionalInterface
    interface ArmatureConstructor<T extends Armature> {
        T construct(Bone rootBone, int boneCount, Map<String, Bone> bonesByName);
    }
}
