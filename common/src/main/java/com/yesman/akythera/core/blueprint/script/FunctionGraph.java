package com.yesman.akythera.core.blueprint.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.util.List;
import java.util.Optional;

/// A function a blueprint declares: the parameters it binds as locals, what it returns, and the graph that
/// runs it.
///
/// @param returnType empty for a function that returns nothing
public record FunctionGraph(List<Parameter> parameters, Optional<ScriptType> returnType, FlowNode body) {
    public static final Codec<FunctionGraph> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Parameter.CODEC.listOf().optionalFieldOf("parameters", List.of()).forGetter(FunctionGraph::parameters),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("return_type")
                    .forGetter(FunctionGraph::returnType),
                FlowNode.CODEC.fieldOf("body").forGetter(FunctionGraph::body)
            ).apply(instance, FunctionGraph::new)
    );

    public FunctionGraph {
        parameters = List.copyOf(parameters);
    }

    public record Parameter(String name, ScriptType type) {
        public static final Codec<Parameter> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Parameter::name),
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("type").forGetter(Parameter::type)
                ).apply(instance, Parameter::new)
        );
    }
}
