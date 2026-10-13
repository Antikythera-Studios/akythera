package com.yesman.akythera.client.api.particle.module;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.ScriptType;

/// Sets a particle up once, right after its emitter spawns it. A built-in module, a Java class registered as a script
/// type with a public no-argument constructor, or a data pack entry run as a [DataDrivenParticleModule].
@ScriptType
public interface ParticleInitializerModule {
    @BlueprintFunction(graphType = ScriptFunction.BlueprintGraphType.EVENT_GRAPH)
    void initialize(final AkytheraParticle particle);

    /// The particles this module sets up. Any other particle skips it.
    default Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraParticle.class;
    }
}
