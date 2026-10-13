package com.yesman.akythera.client.api.particle.module;

import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;

/// A particle module written in a data pack: its event runs the blueprint's graph, once per emitter. Built for
/// either [ParticleInitializerModule] or [ParticleUpdateModule]; only the event of that interface has a graph.
///
/// @param <B> the data pack entry
public final class DataDrivenParticleModule<B extends Blueprint<?, AkytheraParticleEmitter>>
    implements ParticleInitializerModule, ParticleUpdateModule, DataDrivenScript<AkytheraParticleEmitter, B>
{
    private final Holder.Reference<B> blueprint;

    private final AkytheraParticleEmitter owner;

    private final BlueprintExecutor executor;

    /// @param layout [ParticleInitializerModule] or [ParticleUpdateModule], whose event the body implements
    public DataDrivenParticleModule(
        final Holder.Reference<B> blueprint, final BlueprintBody body, final AkytheraParticleEmitter owner, final Class<?> layout
    ) {
        this.blueprint = blueprint;
        this.owner = owner;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(layout), body);
    }

    // ***************************************************************
    // ParticleInitializerModule, ParticleUpdateModule
    // ***************************************************************

    @Override
    public void initialize(final AkytheraParticle particle) {
        executor.invokeMethod("initialize", null, particle);
    }

    @Override
    public void update(final AkytheraParticle particle) {
        executor.invokeMethod("update", null, particle);
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraParticle.class;
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public AkytheraParticleEmitter getScriptOwner() {
        return owner;
    }

    @Override
    public Holder.Reference<B> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return executor.variables();
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
