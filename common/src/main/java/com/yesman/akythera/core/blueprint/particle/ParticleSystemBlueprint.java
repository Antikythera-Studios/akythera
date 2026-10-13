package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.api.particle.module.ParticleModuleContext;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.ScriptType;

import java.util.List;
import java.util.Optional;

/// A data pack entry for a visual effect played as one unit: its emitters, and the parameters a play sets. Synced on
/// join.
///
/// @param variables the parameters a play sets, read by the emitters' modules
@ScriptType(autoRegister = false)
public record ParticleSystemBlueprint(List<ParticleEmitterBlueprint> emitters, List<ScriptVariables.VariableInfo> variables) {
    public static final Codec<ParticleSystemBlueprint> CODEC = RecordCodecBuilder.<ParticleSystemBlueprint>create(
        instance ->
            instance.group(
                ParticleEmitterBlueprint.CODEC.listOf().fieldOf("emitters").forGetter(ParticleSystemBlueprint::emitters),
                ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of()).forGetter(ParticleSystemBlueprint::variables)
            ).apply(instance, ParticleSystemBlueprint::new)
    ).validate(ParticleSystemBlueprint::validate);

    /// Checks unique emitter names, no parameter named [ParticleModuleContext#PARTICLE_LOCAL], and each emitter's
    /// [ParticleEmitterBlueprint#validate].
    public DataResult<ParticleSystemBlueprint> validate() {
        if (emitters.stream().map(ParticleEmitterBlueprint::name).distinct().count() != emitters.size()) {
            return DataResult.error(() -> "Two emitters of a particle system share a name");
        }

        if (variables.stream().anyMatch(variable -> variable.name().equals(ParticleModuleContext.PARTICLE_LOCAL))) {
            return DataResult.error(() -> "A particle system parameter is named " + ParticleModuleContext.PARTICLE_LOCAL + ", the local modules read the particle by");
        }

        for (ParticleEmitterBlueprint emitter : emitters) {
            Optional<DataResult.Error<ParticleEmitterBlueprint>> error = emitter.validate().error();

            if (error.isPresent()) return DataResult.error(error.get().messageSupplier());
        }

        return DataResult.success(this);
    }
}
