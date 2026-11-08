package com.yesman.akythera.api.world.behavior.script;

import com.yesman.akythera.api.world.behavior.BehaviorNode;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;

import java.util.function.Function;

/// A [BehaviorNode] written in a data pack, built for one brain: the variables of its entry and the executor that
/// runs the entry's graphs over them.
///
/// @param <B> the data pack entry of the node
public abstract class DataDrivenBehaviorNode<B extends Blueprint<?, BrainComponent>>
    extends ScriptVariables implements BehaviorNode, DataDrivenScript<BrainComponent, B>
{
    private final Holder.Reference<B> blueprint;

    private final BrainComponent brain;

    private final BlueprintExecutor executor;

    /// @param layoutProvider the class whose blueprint functions the graphs implement
    protected DataDrivenBehaviorNode(
        final Holder.Reference<B> blueprint,
        final BrainComponent brain,
        final Function<B, BlueprintBody> body,
        final Class<?> layoutProvider
    ) {
        super(body.apply(blueprint.value()).variables());

        this.blueprint = blueprint;
        this.brain = brain;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(layoutProvider), body.apply(blueprint.value()), this);
    }

    // ***************************************************************
    // DataDrivenScript
    // ***************************************************************

    @Override
    public BrainComponent getScriptOwner() {
        return brain;
    }

    @Override
    public Holder.Reference<B> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return this;
    }

    @Override
    public BlueprintExecutor getBlueprintExecutor() {
        return executor;
    }
}
