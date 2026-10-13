package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

/// Reads a parameter of the particle system the run belongs to. A module script reads it from its emitter's
/// [AkytheraParticleEmitter#getSystem]. A built-in module's parameter, which runs with no script, reads it from the
/// run's locals, see [AkytheraParticleSystem#getModuleLocals].
///
/// @param type the parameter's type, which source generated from the node spells the read with
public record ParticleSystemParameterNode(String parameter, ScriptType type, int nodeId) implements PureNode {
    /// A node with no canvas row behind it.
    public ParticleSystemParameterNode(final String parameter, final ScriptType type) {
        this(parameter, type, NO_NODE_ID);
    }

    public static final Codec<ParticleSystemParameterNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ParticleSystemParameterNode::parameter),
                    ScriptType.CODEC.fieldOf("type").forGetter(ParticleSystemParameterNode::type),
                    Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(ParticleSystemParameterNode::nodeId)
                )
                .apply(instance, ParticleSystemParameterNode::new)
        );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        if (script != null && script.getScriptOwner() instanceof AkytheraParticleEmitter emitter) {
            return context.returnVal(emitter.getSystem().getParameters().getValue(parameter));
        }

        return context.returnVal(context.locals().getValue(parameter));
    }

    @Override
    public @NonNull String toString() {
        return "Particle System Parameter " + parameter;
    }
}
