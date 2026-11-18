package com.yesman.akythera.core.blueprint.script;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// What a data-driven blueprint authors on top of its parent class.
///
/// The body of a blueprint naming a [Blueprint#dataParent] is laid over that entry's body: it inherits the
/// methods, functions, properties and variables it leaves out, and a [ParentCallNode] in one of its methods or
/// functions runs the parent's graph. A variable declared again under a parent's name replaces the parent's
/// default value.
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

    /// The body of `blueprint`, then the body of each [Blueprint#dataParent] up to the root. Requires a loaded
    /// registry access.
    ///
    /// @throws IllegalStateException when a data parent is missing from the registry, or the parents form a loop
    public static <B extends Blueprint<?, ?>> List<Link<B>> chain(final Holder.Reference<B> blueprint) {
        List<Link<B>> chain = new ArrayList<>();
        Set<ResourceKey<B>> visited = new HashSet<>();
        Holder.Reference<B> current = blueprint;

        chain.add(new Link<>(current, current.value().body()));
        visited.add(current.key());

        while (current.value().dataParent().isPresent()) {
            ResourceKey<B> parentKey = ResourceKey.create(blueprint.key().registryKey(), current.value().dataParent().get());

            if (!visited.add(parentKey)) {
                throw new IllegalStateException("Circular blueprint hierarchy: " + parentKey.identifier());
            }

            current = StaticRegistryAccess.getRegistryAccess().getOrThrow(parentKey);
            chain.add(new Link<>(current, current.value().body()));
        }

        return chain;
    }

    /// `bodies`, a body then the body of each of its data parents up to the root, as one body: each method,
    /// function, property and variable is the one of the nearest body declaring it. The variables keep the root's
    /// order.
    public static BlueprintBody fold(final List<BlueprintBody> bodies) {
        if (bodies.size() == 1) return bodies.getFirst();

        Map<String, FlowNode> methods = new LinkedHashMap<>();
        Map<String, FunctionGraph> functions = new LinkedHashMap<>();
        Map<String, JsonElement> properties = new LinkedHashMap<>();
        Map<String, ScriptVariables.VariableInfo> variables = new LinkedHashMap<>();

        for (BlueprintBody body : bodies.reversed()) {
            methods.putAll(body.methods);
            functions.putAll(body.functions);
            properties.putAll(body.properties);
            body.variables.forEach(variable -> variables.put(variable.name(), variable));
        }

        return new BlueprintBody(methods, functions, properties, List.copyOf(variables.values()));
    }

    /// The body of `blueprint` laid over its data parents, see [#chain] and [#fold].
    public static <B extends Blueprint<?, ?>> BlueprintBody inherited(final Holder.Reference<B> blueprint) {
        return fold(chain(blueprint).stream().map(Link::body).toList());
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

    /// One body of a [#chain].
    ///
    /// @param blueprint the entry holding `body`
    public record Link<B extends Blueprint<?, ?>>(Holder.Reference<B> blueprint, BlueprintBody body) {
    }
}
