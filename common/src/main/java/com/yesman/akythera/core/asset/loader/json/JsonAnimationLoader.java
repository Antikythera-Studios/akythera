package com.yesman.akythera.core.asset.loader.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryAnimationLoader;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Load animationsToReplace from json files. Instances are dedicated for loading a resource specified by [#resourcePath].
///
/// Deprecated: Use [BinaryAnimationLoader] with .efasset extension to accelerate asset loading process
@Deprecated
public final class JsonAnimationLoader implements AnimationLoader, JsonAssetLoader {
    private static final String LEGACY_ROOT_BONE_NAME = "Coord";

    private final Identifier resourcePath;
    private final JsonObject jsonObject;
    private final Metadata metadata;

    public JsonAnimationLoader(Identifier resourcePath, Resource resource, Metadata metadata)
        throws ResourceLoadingFailureException
    {
        this.resourcePath = resourcePath;
        jsonObject = readJsonObject(resource);
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

    @Override
    public AnimationClip loadRawAnimationClip(@NonNull AssetAccessor<? extends Armature> armature)
        throws ResourceLoadingFailureException
    {
        try {
            TransformFormat transformFormat = JsonAssetLoader.getAsTransformFormatOrDefault(jsonObject, "format");
            JsonArray array = GsonHelper.getAsJsonArray(jsonObject, "animation");
            AnimationClip.Builder clipBuilder = AnimationClip.builder();
            boolean root = true;

            for (JsonElement element : array) {
                JsonObject jointAnimation = GsonHelper.convertToJsonObject(element, "animation");
                String name = GsonHelper.getAsString(jointAnimation, "name");
                Bone bone = armature.get().getBoneByName(name);

                if (bone == null) {
                    if (!LEGACY_ROOT_BONE_NAME.equals(name)) {
                        // Skip the bone that doesn't exist in armature
                        root = false;
                        continue;
                    } else {
                        // Add a fake bone to load old root motion bone
                        bone = new Bone(LEGACY_ROOT_BONE_NAME, 0, new Matrix4f());
                    }
                }

                BoneAnimationTrack sheet = getTransformSheet(jointAnimation, bone.getLocalTransform().invert(new Matrix4f()), root, transformFormat);
                clipBuilder.addBoneAnimation(name, sheet);
                root = false;
            }

            return clipBuilder.build();
        } catch (JsonSyntaxException e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    /// @param invLocalTransform    nullable if transformFormat == [TransformFormat#ATTRIBUTES]. When fully port matrix animation to attribute based animation, remove this parameter.
    /// @param rootCorrection       no matter what the value is if transformFormat == [TransformFormat#ATTRIBUTES]
    private BoneAnimationTrack getTransformSheet(JsonObject jointAnimationObject, @Nullable Matrix4f invLocalTransform, boolean rootCorrection, TransformFormat transformFormat)
        throws JsonSyntaxException
    {
        JsonArray timeArray = GsonHelper.getAsJsonArray(jointAnimationObject, "time");
        JsonArray transformArray = GsonHelper.getAsJsonArray(jointAnimationObject, "transform");

        if (timeArray.size() != transformArray.size()) {
            throw new ResourceLoadingFailureException(
                resourcePath,
                "Can't read transform sheet: the size of timestamp and transform array is different."
                    + "timestamp array size: " + timeArray.size() + ", transform array size: " + transformArray.size()
            );
        }

        int timesCount = timeArray.size();
        List<Keyframe> keyframeList = new ArrayList<>();
        float[] tempMatrixStorage = new float[16];

        for (int i = 0; i < timesCount; i++) {
            float timeStamp = timeArray.get(i).getAsFloat();

            if (timeStamp < 0.0F) {
                continue;
            }

            // WORKAROUND: The case when transform format is wrongly specified!
            if (transformFormat == TransformFormat.ATTRIBUTES && transformArray.get(i).isJsonArray()) {
                transformFormat = TransformFormat.MATRIX;
            } else if (transformFormat == TransformFormat.MATRIX && transformArray.get(i).isJsonObject()) {
                transformFormat = TransformFormat.ATTRIBUTES;
            }

            switch (transformFormat) {
                case MATRIX -> {
                    JsonArray matrixArray = GsonHelper.convertToJsonArray(transformArray.get(i), "transform");

                    for (int j = 0; j < 16; j++) {
                        tempMatrixStorage[j] = GsonHelper.convertToFloat(matrixArray.get(j), "matrix");
                    }

                    Matrix4f matrix = new Matrix4f().set(tempMatrixStorage);
                    matrix.transpose();

                    if (rootCorrection) {
                        BLENDER_TO_MINECRAFT_COORD_SYSTEM.mul(matrix, matrix);
                    }

                    assert invLocalTransform != null;
                    invLocalTransform.mul(matrix, matrix);

                    Transform transform = Transform.fromMatrix(matrix);
                    transform.rot().normalize();
                    keyframeList.add(new Keyframe(timeStamp, transform));
                }
                case ATTRIBUTES -> {
                    JsonObject transformObject = GsonHelper.convertToJsonObject(transformArray.get(i), "transform");
                    JsonArray locArray = GsonHelper.getAsJsonArray(transformObject, "loc");
                    JsonArray rotArray = GsonHelper.getAsJsonArray(transformObject, "rot");
                    JsonArray scaArray = GsonHelper.getAsJsonArray(transformObject, "sca");
                    Transform transform =
                        new Transform(
                            new Vec3(
                                GsonHelper.convertToFloat(locArray.get(0), "loc"),
                                GsonHelper.convertToFloat(locArray.get(1), "loc"),
                                GsonHelper.convertToFloat(locArray.get(2), "loc")
                            ),
                            new Quaterniond(
                                GsonHelper.convertToFloat(rotArray.get(1), "rot"),
                                GsonHelper.convertToFloat(rotArray.get(2), "rot"),
                                GsonHelper.convertToFloat(rotArray.get(3), "rot"),
                                GsonHelper.convertToFloat(rotArray.get(0), "rot")
                            ),
                            new Vec3(
                                GsonHelper.convertToFloat(scaArray.get(0), "sca"),
                                GsonHelper.convertToFloat(scaArray.get(1), "sca"),
                                GsonHelper.convertToFloat(scaArray.get(2), "sca")
                            )
                        );

                    keyframeList.add(new Keyframe(timeStamp, transform));
                }
            }
        }

        return new BoneAnimationTrack(keyframeList);
    }
}
