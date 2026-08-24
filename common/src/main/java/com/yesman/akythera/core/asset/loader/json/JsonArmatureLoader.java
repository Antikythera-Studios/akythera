package com.yesman.akythera.core.asset.loader.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Map;

/// Load meshes from json files. Instances are dedicated for loading a resource specified by [#resourcePath].
///
/// @Deprecated Use [BinaryArmatureLoader] with binary assets to accelerate asset loading process
@Deprecated
public final class JsonArmatureLoader implements ArmatureLoader, JsonAssetLoader {
    private final Identifier resourcePath;
    private final JsonObject jsonObject;

    public JsonArmatureLoader(Identifier resourcePath, Resource resource)
        throws ResourceLoadingFailureException
    {
        this.resourcePath = resourcePath;
        jsonObject = readJsonObject(resource);
    }

    @Override
    public Identifier resourcePath() {
        return resourcePath;
    }

    @Override
    public <T extends Armature> T loadArmature(ArmatureConstructor<T> armatureConstructor)
        throws ResourceLoadingFailureException
    {
        try {
            JsonObject armatureObj = GsonHelper.getAsJsonObject(jsonObject, "armature");
            TransformFormat transformFormat = JsonAssetLoader.getAsTransformFormatOrDefault(jsonObject, "armature_format");
            JsonObject hierarchyObj = GsonHelper.convertToJsonObject(GsonHelper.getAsJsonArray(armatureObj, "hierarchy").get(0), "root_armature");
            JsonArray nameAsVertexGroups = GsonHelper.getAsJsonArray(armatureObj, "joints");
            Map<String, Integer> boneNamesToIds = new HashMap<>();
            int id = 0;

            for (int i = 0; i < nameAsVertexGroups.size(); i++) {
                String name = GsonHelper.convertToString(nameAsVertexGroups.get(i), "joints");
                boneNamesToIds.put(name, id);
                id++;
            }

            Map<String, Bone> bones = new HashMap<>();
            Bone bone = readBone(hierarchyObj, boneNamesToIds, bones, transformFormat, true);
            bone.initOriginTransform(new Matrix4f());

            return armatureConstructor.construct(bone, bones.size(), bones);
        } catch (Exception e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    private Bone readBone(JsonObject object, Map<String, Integer> boneNamesToIds, Map<String, Bone> boneNameToBone, TransformFormat transformFormat, boolean root)
        throws JsonSyntaxException, ResourceLoadingFailureException
    {
        String name = GsonHelper.getAsString(object, "name");

        if (!boneNamesToIds.containsKey(name)) {
            throw new ResourceLoadingFailureException(resourcePath, "Bone named " + name + " doesn't exist in armature hierarchy");
        }

        JsonElement transform = GsonHelper.getNonNull(object, "transform");

        // WORKAROUND: The case when transform format is wrongly specified!
        if (transformFormat == TransformFormat.ATTRIBUTES && transform.isJsonArray()) {
            transformFormat = TransformFormat.MATRIX;
        } else if (transformFormat == TransformFormat.MATRIX && transform.isJsonObject()) {
            transformFormat = TransformFormat.ATTRIBUTES;
        }

        Matrix4f localMatrix = null;

        switch (transformFormat) {
            case MATRIX -> {
                float[] floatArray = ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.convertToJsonArray(transform, "transform"));
                localMatrix = new Matrix4f().set(floatArray);
                localMatrix.transpose();

                if (root) {
                    BLENDER_TO_MINECRAFT_COORD_SYSTEM.mul(localMatrix, localMatrix);
                }
            }
            case ATTRIBUTES -> {
                JsonObject transformObject = transform.getAsJsonObject();
                JsonArray locArray = transformObject.get("loc").getAsJsonArray();
                JsonArray rotArray = transformObject.get("rot").getAsJsonArray();
                JsonArray scaArray = transformObject.get("sca").getAsJsonArray();
                Transform boneLocalTransform =
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
                localMatrix = boneLocalTransform.toMatrix();

                if (root) {
                    BLENDER_TO_MINECRAFT_COORD_SYSTEM.mul(localMatrix, localMatrix);
                }
            }
        }

        Bone bone = new Bone(name, boneNamesToIds.get(name), localMatrix);
        boneNameToBone.put(name, bone);

        if (object.has("children")) {
            for (JsonElement children : GsonHelper.getAsJsonArray(object, "children")) {
                bone.addChildBones(readBone(GsonHelper.convertToJsonObject(children, "child_bone"), boneNamesToIds, boneNameToBone, transformFormat, false));
            }
        }

        return bone;
    }

}