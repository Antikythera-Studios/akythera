package com.yesman.akythera.core.asset.loader.binary;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/// Load armatures from binary files. Instances are dedicated for loading a resource specified by [#resourcePath].
public final class BinaryArmatureLoader implements ArmatureLoader, BinaryAssetLoader {
    private final Identifier resourcePath;
    private final Resource resource;

    public BinaryArmatureLoader(Identifier resourcePath, Resource resource)
        throws ResourceLoadingFailureException
    {
        this.resourcePath = resourcePath;
        this.resource = resource;
    }

    @Override
    public Identifier resourcePath() {
        return resourcePath;
    }

    @Override
    public <T extends Armature> T loadArmature(ArmatureConstructor<T> armatureConstructor)
        throws ResourceLoadingFailureException
    {
        try (InputStream inputStream = resource.open()) {
            byte[] decompressed = decompress(inputStream.readAllBytes());
            var payloadBlocks = blocksLookup(decompressed);

            if (!checkArmatureBlocks(payloadBlocks)) {
                throw new ResourceLoadingFailureException(resourcePath(), "Missing skeletal mesh data.");
            }

            Map<String, Integer> boneNamesToIds = new HashMap<>();
            Map<String, Bone> bonesByName = new HashMap<>();
            Bone rootBone = null;

            for (PayloadBlock payloadBlock : payloadBlocks) {
                switch (payloadBlock.dataType()) {
                case ARM_BONE_NAMES -> {
                    int offset = payloadBlock.bodyOffset();
                    int count = BinaryAssetLoader.readInt(decompressed, offset);
                    offset+=4;

                    for (int i = 0; i < count; i++) {
                        String boneName = BinaryAssetLoader.readString(decompressed, offset);
                        offset += 4+boneName.length();
                        boneNamesToIds.put(boneName, i);
                    }
                }
                case ARM_HIERARCHY -> {
                    if (boneNamesToIds.isEmpty()) {
                        throw new ResourceLoadingFailureException(resourcePath(), "Armature hierarchy block declared before bone names block.");
                    }

                    int offset = payloadBlock.bodyOffset();
                    int count = BinaryAssetLoader.readInt(decompressed, offset);
                    offset += 4;

                    if (count == 0) {
                        throw new ResourceLoadingFailureException(resourcePath(), "No bones to read");
                    } else if (count == 1) {
                        rootBone = readIdBonePair(decompressed, offset, boneNamesToIds, bonesByName, true).getSecond();
                    } else {
                        List<Bone> multiRoots = new ArrayList<>();

                        for (int i = 0; i < count; i++) {
                            Pair<Integer, Bone> parseResult = readIdBonePair(decompressed, offset, boneNamesToIds, bonesByName, true);
                            offset = parseResult.getFirst();
                            multiRoots.add(parseResult.getSecond());
                        }

                        String newRootBoneName = "generated_root";
                        int duplCount = 0;

                        // Search name that is not duplicated from original entry
                        while (bonesByName.containsKey(newRootBoneName)) {
                            newRootBoneName = "generated_root" + duplCount;
                        }

                        // Creates a new root bone
                        rootBone = new Bone(newRootBoneName, boneNamesToIds.size(), new Matrix4f());
                        bonesByName.put(newRootBoneName, rootBone);
                        rootBone.addChildBones(multiRoots.toArray(new Bone[0]));
                    }
                }
                }
            }

            rootBone.initOriginTransform(new Matrix4f());

            return armatureConstructor.construct(rootBone, bonesByName.size(), bonesByName);
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    /// Check for data blocks required by loading an armature
    private static boolean checkArmatureBlocks(final List<BinaryAssetLoader.PayloadBlock> payloadBlocks) {
        // Common mesh blocks for static & skeletal
        boolean hasBoneNames = false;
        boolean hasHierarchy = false;

        for (BinaryAssetLoader.PayloadBlock payloadBlock : payloadBlocks) {
            hasBoneNames |= payloadBlock.dataType() == BinaryAssetLoader.DataType.ARM_BONE_NAMES;
            hasHierarchy |= payloadBlock.dataType() == BinaryAssetLoader.DataType.ARM_HIERARCHY;
        }

        return hasBoneNames && hasHierarchy;
    }

    /// Read the whole bone hierarchy
    ///
    /// @param boneNamesToIds   read-only bone id map by names
    /// @param bones            destination to gather bones
    private static Pair<Integer, Bone> readIdBonePair(
        final byte[] bytes,
        int offset,
        final Map<String, Integer> boneNamesToIds,
        Map<String, Bone> bones,
        boolean correctRoot
    ) {
        String boneName = BinaryAssetLoader.readString(bytes, offset);
        offset+=4+boneName.length();

        float[] locArr = BinaryAssetLoader.readFloatArray(bytes, offset, 3);
        float[] rotArr = BinaryAssetLoader.readFloatArray(bytes, offset + 12, 4);
        float[] scaArr = BinaryAssetLoader.readFloatArray(bytes, offset + 28, 3);
        Transform boneLocalTransform =
            new Transform(
                new Vec3(locArr[0], locArr[1], locArr[2]),
                new Quaterniond(rotArr[1], rotArr[2], rotArr[3], rotArr[0]),
                new Vec3(scaArr[0], scaArr[1], scaArr[2])
            );

        Matrix4f boneLocalMatrix = boneLocalTransform.toMatrix();

        if (correctRoot) {
            BLENDER_TO_MINECRAFT_COORD_SYSTEM.mul(boneLocalMatrix, boneLocalMatrix);
        }

        Bone bone = new Bone(boneName, boneNamesToIds.get(boneName), boneLocalMatrix);
        bones.put(boneName, bone);

        int childCount = BinaryAssetLoader.readInt(bytes, offset + 40);
        offset += 44;

        for (int i = 0; i < childCount; i++) {
            Pair<Integer, Bone> boneResult = readIdBonePair(bytes, offset, boneNamesToIds, bones, false);
            bone.addChildBones(boneResult.getSecond());
            offset = boneResult.getFirst();
        }

        return Pair.of(offset, bone);
    }
}
