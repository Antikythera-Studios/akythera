package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import org.jspecify.annotations.Nullable;


/// One run of a built-in module's parameters for one particle. The parameters read the particle as the local
/// [#PARTICLE_LOCAL] and the system's parameters as locals under their own names, from the system's
/// [AkytheraParticleSystem#getModuleLocals].
public final class ParticleModuleContext {
    public static final String PARTICLE_LOCAL = "particle";

    /// The value of a built-in module's parameter left unset. Answers nothing, so the module uses its own default.
    public static final PureNode UNSET = PureNode.of(context -> null);

    /// The field a built-in module's parameter `name` is written in, [#UNSET] when absent and left out of the json
    /// when unset.
    public static MapCodec<PureNode> parameter(final String name) {
        return PureNode.CODEC.optionalFieldOf(name, UNSET);
    }

    private final ScriptNode.ExecutionContext context;

    /// @param module the module's name, recorded as the run's site under the emitter
    public ParticleModuleContext(final AkytheraParticle particle, final String module) {
        AkytheraParticleSystem system = particle.getEmitter().getSystem();
        ScriptVariables locals = system.getModuleLocals();
        locals.setValue(PARTICLE_LOCAL, particle);

        this.context = ScriptRecorder.newContext(
            new ScriptCaller(system, null, particle.getEmitter().getBlueprint().name() + ScriptCaller.SLOT_SEPARATOR + module),
            locals
        );
    }

    /// What `node` answers, or `fallback` when it answers no `T` or throws.
    @SuppressWarnings("unchecked")
    public <T> T evaluate(final PureNode node, final T fallback) {
        Object value = run(node);

        return fallback.getClass().isInstance(value) ? (T)value : fallback;
    }

    /// The number `node` answers, or `fallback` when it answers no number or throws.
    public float evaluateFloat(final PureNode node, final float fallback) {
        return run(node) instanceof Number number ? number.floatValue() : fallback;
    }

    /// The number `node` answers, or `fallback` when it answers no number or throws.
    public int evaluateInt(final PureNode node, final int fallback) {
        return run(node) instanceof Number number ? number.intValue() : fallback;
    }

    private @Nullable Object run(final PureNode node) {
        try {
            return node.execute(null, context);
        } catch (Throwable throwable) {
            ScriptRecorder.recordFailure(context, throwable);

            return null;
        }
    }
}
