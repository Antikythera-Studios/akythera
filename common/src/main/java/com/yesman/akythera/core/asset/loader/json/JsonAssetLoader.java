package com.yesman.akythera.core.asset.loader.json;

import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/// Common json loader helper that read and parse the whole file content into a root [JsonObject]
///
/// Since the Epic Fight 16 version (akythera), we support binary files (.akasset) to optimize file
/// size and loading process.
///
/// The .json loader exists for old assets' support, especially retargeting animations in AkytheraLabs.
public interface JsonAssetLoader {
    Identifier resourcePath();

    /// Extract a [TransformFormat] from a given json object.
    ///
    /// [TransformFormat#ATTRIBUTES] is a newly featured transform expression type that only requires 10 floating points
    /// while [TransformFormat#MATRIX] requires 16.
    static TransformFormat getAsTransformFormatOrDefault(JsonObject jsonObject, String propertyName) {
        return jsonObject.has(propertyName) ? ParseUtils.enumValueOfOrNull(TransformFormat.class, GsonHelper.getAsString(jsonObject, propertyName)) : TransformFormat.MATRIX;
    }

    /// Parse resource into root [JsonObject]
    default JsonObject readJsonObject(Resource resource) throws ResourceLoadingFailureException {
        try (
            InputStream inputStream = resource.open();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            JsonReader jsonReader = new JsonReader(inputStreamReader)
        ) {
            return GsonHelper.convertToJsonObject(Streams.parse(jsonReader), "root_object");
        } catch (IOException e) {
            throw new ResourceLoadingFailureException(resourcePath(), e);
        }
    }

    /// Determines how the transform is expressed in json
    ///
    /// [TransformFormat#MATRIX] be like,
    ///
    /// (0, 1, 2, ..., 15)
    ///
    /// [TransformFormat#ATTRIBUTES] be like,
    /// ```
    /// {
    ///   "loc": (0, 0, 0),
    ///   "rot": (0, 0, 0, 1),
    ///   "sca": (1, 1, 1),
    /// }
    /// ```
    /// The attribute format is more recommended since animation transforms are always stored in [Transform] that is
    /// represented by loc, rot, scale
    enum TransformFormat {
        MATRIX, ATTRIBUTES
    }
}
