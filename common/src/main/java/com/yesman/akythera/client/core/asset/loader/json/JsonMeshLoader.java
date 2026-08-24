package com.yesman.akythera.client.core.asset.loader.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.client.core.asset.loader.binary.BinaryMeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.json.JsonAssetLoader;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;

import java.util.Map;

/// Load meshes from json files. Instances are dedicated for loading a resource specified by [#resourcePath].
///
/// Deprecated: Use [BinaryMeshLoader] with .akasset extension to accelerate asset loading process
@Deprecated
public final class JsonMeshLoader implements MeshLoader, JsonAssetLoader {
    private final Identifier resourcePath;
    private final JsonObject jsonObject;
    private final Metadata metadata;

    /// @param resourcePath a resource path identifier
    /// @param resource     a resource object
    public JsonMeshLoader(Identifier resourcePath, Resource resource, Metadata metadata)
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
    public <T extends StaticMesh> T loadStaticMesh(MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor)
        throws ResourceLoadingFailureException
    {
        try {
            JsonObject obj = GsonHelper.getAsJsonObject(jsonObject, "vertices");
            JsonObject positions = GsonHelper.getAsJsonObject(obj, "positions");
            JsonObject normals = GsonHelper.getAsJsonObject(obj, "normals");
            JsonObject uvs = GsonHelper.getAsJsonObject(obj, "uvs");
            // Backward compatibility
            JsonObject parts = obj.has("parts") ? GsonHelper.getAsJsonObject(obj, "parts") : null;
            // Backward compatibility
            JsonObject indices = obj.has("indices") ? GsonHelper.getAsJsonObject(obj, "indices") : null;

            float[] convertedPositions = convertZUpVectorArrayIntoYUpArray("position", GsonHelper.getAsJsonArray(positions, "array"));
            float[] convertedNormals = convertZUpVectorArrayIntoYUpArray("normal", GsonHelper.getAsJsonArray(normals, "array"));
            float[] uvArray = ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.getAsJsonArray(uvs, "array"));

            Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart> staticMeshBuilder =
                StaticMesh.builder()
                    .positions(convertedPositions)
                    .normals(convertedNormals)
                    .uvs(uvArray);

            if (parts != null) {
                for (Map.Entry<String, JsonElement> e : parts.entrySet()) {
                    staticMeshBuilder.addPart(e.getKey(), VertexBuilder.create(ParseUtils.jsonArrayToPrimitiveInt(e.getValue().getAsJsonObject().get("array").getAsJsonArray())));
                }
            }

            if (indices != null) {
                staticMeshBuilder.addPart(UNGROUPED_PART_NAME, VertexBuilder.create(ParseUtils.jsonArrayToPrimitiveInt(indices.get("array").getAsJsonArray())));
            }

            staticMeshBuilder.metadata(metadata);

            return meshConstructor.construct(staticMeshBuilder);
        } catch (Exception e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    @Override
    public <T extends SkeletalMesh> T loadSkeletalMesh(MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor)
        throws ResourceLoadingFailureException
    {
        try {
            JsonObject obj = jsonObject.getAsJsonObject("vertices");
            JsonObject positions = GsonHelper.getAsJsonObject(obj, "positions");
            JsonObject normals = GsonHelper.getAsJsonObject(obj, "normals");
            JsonObject uvs = GsonHelper.getAsJsonObject(obj, "uvs");
            JsonObject deformationWeights = GsonHelper.getAsJsonObject(obj, "weights");
            JsonArray affectingBoneCounts = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(obj, "vcounts"), "array");
            JsonArray affectingBoneIdsAndWeightIndices = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(obj, "vindices"), "array");

            // Backward compatibility
            JsonObject parts = obj.has("parts") ? GsonHelper.getAsJsonObject(obj, "parts") : null;
            // Backward compatibility
            JsonObject indices = obj.has("indices") ? GsonHelper.getAsJsonObject(obj, "indices") : null;

            float[] convertedPositions = convertZUpVectorArrayIntoYUpArray("position", GsonHelper.getAsJsonArray(positions, "array"));
            float[] convertedNormals = convertZUpVectorArrayIntoYUpArray("normal", GsonHelper.getAsJsonArray(normals, "array"));
            float[] uvArray = ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.getAsJsonArray(uvs, "array"));
            float[] weightArray = ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.getAsJsonArray(deformationWeights, "array"));
            int[] affectingBoneIdArray = new int[affectingBoneIdsAndWeightIndices.size()/2];
            int[] affectingWeightIndexArray = new int[affectingBoneIdsAndWeightIndices.size()/2];
            int[] deformationFromToArray = new int[affectingBoneCounts.size()*2];

            for (int i = 0; i < affectingBoneIdsAndWeightIndices.size() / 2; i++) {
                affectingBoneIdArray[i] = GsonHelper.convertToInt(affectingBoneIdsAndWeightIndices.get(i*2), "vindices");
                affectingWeightIndexArray[i] = GsonHelper.convertToInt(affectingBoneIdsAndWeightIndices.get(i*2+1), "vindices");
            }

            int from = 0;

            for (int i = 0; i < affectingBoneCounts.size(); i++) {
                int next = GsonHelper.convertToInt(affectingBoneCounts.get(i), "vcounts");
                deformationFromToArray[i*2] = from;
                deformationFromToArray[i*2+1] = from + next;
                from += next;
            }

            SkeletalMesh.SkeletalMeshBuilder skeletalMeshBuilder = (SkeletalMesh.SkeletalMeshBuilder)
                SkeletalMesh.builder()
                    .weights(weightArray)
                    .affectingBoneIdArray(affectingBoneIdArray)
                    .affectingWeightIndexArray(affectingWeightIndexArray)
                    .deformationFromToArray(deformationFromToArray)
                    .positions(convertedPositions)
                    .normals(convertedNormals)
                    .uvs(uvArray)
                ;

            if (parts != null) {
                for (Map.Entry<String, JsonElement> e : parts.entrySet()) {
                    skeletalMeshBuilder.addPart(e.getKey(), VertexBuilder.create(ParseUtils.jsonArrayToPrimitiveInt(e.getValue().getAsJsonObject().get("array").getAsJsonArray())));
                }
            }

            if (indices != null) {
                skeletalMeshBuilder.addPart(UNGROUPED_PART_NAME, VertexBuilder.create(ParseUtils.jsonArrayToPrimitiveInt(indices.get("array").getAsJsonArray())));
            }

            skeletalMeshBuilder.metadata(metadata);

            return meshConstructor.construct(skeletalMeshBuilder);
        } catch (Exception e) {
            throw new ResourceLoadingFailureException(resourcePath, e);
        }
    }

    /// Convert Blender space vector array into Minecraft's Y-Up coord vector list
    ///
    /// @param elementName the json element name to log when exception occurs in parsing
    private static float[] convertZUpVectorArrayIntoYUpArray(String elementName, JsonArray vectorArray)
        throws JsonSyntaxException
    {
        float[] convertedVectors = new float[vectorArray.size()];

        for (int i = 0; i < vectorArray.size() / 3; i++) {
            int k = i * 3;
            Vector3f vector = new Vector3f(
                GsonHelper.convertToFloat(vectorArray.get(k), String.format("%s[%d]", elementName, k)),
                GsonHelper.convertToFloat(vectorArray.get(k+1), String.format("%s[%d]", elementName, k+1)),
                GsonHelper.convertToFloat(vectorArray.get(k+2), String.format("%s[%d]", elementName, k+2))
            );

            // Convert from blender space to GL coord system
            vector.mul(BLENDER_TO_MINECRAFT_COORD_SYSTEM_3F);
            convertedVectors[k] = vector.x;
            convertedVectors[k+1] = vector.y;
            convertedVectors[k+2] = vector.z;
        }

        return convertedVectors;
    }
}