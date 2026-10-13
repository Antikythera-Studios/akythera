package com.yesman.akythera.core.blueprint.script;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Map;

/// What a data-driven blueprint authors on top of its parent class.
///
/// @param methods    the graphs implementing the parent's [BlueprintFunction] methods, every event and each
///                   overridden function, by [BlueprintLayout.Signature#key]
/// @param functions  the functions the blueprint declares itself, by name
/// @param properties the parent's property values in their type's codec form, by
///                   [BlueprintLayout.Property#key]
/// @param variables  the member variables
public record BlueprintBody(
    Map<String, FlowNode> methods,
    Map<String, FunctionGraph> functions,
    Map<String, JsonElement> properties,
    List<ScriptVariables.VariableInfo> variables
) {
    public static final BlueprintBody EMPTY = new BlueprintBody(Map.of(), Map.of(), Map.of(), List.of());

    /// Writes the four fields beside the fields of the blueprint that holds the body.
    public static final MapCodec<BlueprintBody> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, FlowNode.CODEC).optionalFieldOf("methods", Map.of())
                    .forGetter(BlueprintBody::methods),
                Codec.unboundedMap(Codec.STRING, FunctionGraph.CODEC).optionalFieldOf("functions", Map.of())
                    .forGetter(BlueprintBody::functions),
                Codec.unboundedMap(Codec.STRING, ExtraCodecs.JSON).optionalFieldOf("properties", Map.of())
                    .forGetter(BlueprintBody::properties),
                ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                    .forGetter(BlueprintBody::variables)
            ).apply(instance, BlueprintBody::new)
    );

    public BlueprintBody {
        methods = Map.copyOf(methods);
        functions = Map.copyOf(functions);
        properties = Map.copyOf(properties);
        variables = List.copyOf(variables);
    }

    /// The value this body sets for the property `name` of `layoutProvider`, decoded through the property's
    /// type, or `fallback` when the body leaves it unset.
    ///
    /// @throws IllegalArgumentException when `layoutProvider` has no such property
    /// @throws IllegalStateException    when the value does not decode
    public <T> T property(final Class<?> layoutProvider, final String name, final T fallback) {
        BlueprintLayout.Property property = ScriptModularizeHelper.scanBlueprintLayout(layoutProvider).property(name);

        if (property == null) {
            throw new IllegalArgumentException(layoutProvider.getName() + " has no blueprint property " + name);
        }

        JsonElement value = properties.get(property.key());

        return value == null || property.type().codec() == null ? fallback : property.type().parse(value);
    }
}
