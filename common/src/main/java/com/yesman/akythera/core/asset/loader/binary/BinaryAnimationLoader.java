package com.yesman.akythera.core.asset.loader.binary;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/// Load animationsToReplace from binary files. Instances are dedicated for loading a resource specified by [#resourcePath].
public final class BinaryAnimationLoader implements AnimationLoader, BinaryAssetLoader {
    private final Identifier resourcePath;
    private final Resource resource;
    private final Metadata metadata;

    public BinaryAnimationLoader(final Identifier resourcePath, final Resource resource, final Metadata metadata)
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

    /// @param armature No use for binary loader, just exists for .json
    @Override
    public AnimationClip loadRawAnimationClip(@Nullable final AssetAccessor<? extends Armature> armature)
        throws ResourceLoadingFailureException
    {
        try (InputStream inputStream = resource.open()) {
            byte[] decompressed = decompress(inputStream.readAllBytes());
            var payloadBlocks = blocksLookup(decompressed);

            if (!checkAnimationBlocks(payloadBlocks)) {
                throw new ResourceLoadingFailureException(resourcePath(), "Missing bone animation track data.");
            }

            AnimationClip.Builder animClipBuilder = AnimationClip.builder();

            for (PayloadBlock payloadBlock : payloadBlocks) {
                switch (payloadBlock.dataType()) {
                case ANIM_BONE_TRACK -> {
                    Pair<String, BoneAnimationTrack> result = readBoneAnimationTrack(decompressed, payloadBlock.bodyOffset());
                    animClipBuilder.addBoneAnimation(result.getFirst(), result.getSecond());
                }
                case META_FPS -> {
                    // No action since FPS is already applied to frame rates in the exporter side
                }
                }
            }

            return animClipBuilder.build();
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    /// Check for data blocks required by loading an armature
    private static boolean checkAnimationBlocks(final List<BinaryAssetLoader.PayloadBlock> payloadBlocks) {
        for (BinaryAssetLoader.PayloadBlock payloadBlock : payloadBlocks) {
            if (payloadBlock.dataType() == BinaryAssetLoader.DataType.ANIM_BONE_TRACK) {
                return true;
            }
        }

        return false;
    }

    /// @return (Bone name, anim transform) pair
    private static Pair<String, BoneAnimationTrack> readBoneAnimationTrack(final byte[] bytes, int offset) {
        String boneName = BinaryAssetLoader.readString(bytes, offset);
        offset += 4 + boneName.length();
        int count = BinaryAssetLoader.readInt(bytes, offset);

        offset+=4;
        List<Keyframe> keyframes = new ArrayList<>(count);
        float[] timestamps = BinaryAssetLoader.readFloatArray(bytes, offset, count);
        // multiply the number of elements in transform (loc, rot, sca)
        float[] transforms = BinaryAssetLoader.readFloatArray(bytes, offset + timestamps.length * 4, count * 10);

        for (int i = 0; i < count; i++) {
            float timeStamp = timestamps[i];
            Transform transform =
                new Transform(
                    new Vec3(
                        transforms[i*10],
                        transforms[i*10+1],
                        transforms[i*10+2]
                    ),
                    new Quaterniond(
                        transforms[i*10+4],
                        transforms[i*10+5],
                        transforms[i*10+6],
                        transforms[i*10+3]
                    ),
                    new Vec3(
                        transforms[i*10+7],
                        transforms[i*10+8],
                        transforms[i*10+9]
                    )
                );

            keyframes.add(new Keyframe(timeStamp, transform));
        }

        return Pair.of(boneName, new BoneAnimationTrack(keyframes));
    }
}
