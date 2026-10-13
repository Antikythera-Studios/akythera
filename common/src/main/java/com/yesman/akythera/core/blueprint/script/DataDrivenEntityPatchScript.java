package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.DataDrivenEntityPatchBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;

/// Runs the [EntityPatchScript] events of a [DataDrivenEntityPatchBlueprint].
public final class DataDrivenEntityPatchScript
    implements EntityPatchScript, DataDrivenScript<EntityPatch<Entity>, DataDrivenEntityPatchBlueprint>
{
    /// [ScriptCaller] sites of the three entry points, which are the events' method names.
    public static final String ON_CONSTRUCT = "onConstruct";
    public static final String ON_JOIN_WORLD = "onJoinWorld";
    public static final String UPDATE = "update";

    /// Script blueprint
    private final Holder.Reference<DataDrivenEntityPatchBlueprint> blueprint;

    private final EntityPatch<Entity> blueprintOwner;

    private final BlueprintExecutor executor;

    public DataDrivenEntityPatchScript(final Holder.Reference<DataDrivenEntityPatchBlueprint> blueprint, EntityPatch<Entity> blueprintOwner) {
        this.blueprint = blueprint;
        this.blueprintOwner = blueprintOwner;
        this.executor = new BlueprintExecutor(
            this, ScriptModularizeHelper.scanBlueprintLayout(EntityPatchScript.class), blueprint.value().body());
    }

    @Override
    public void onConstruct() {
        executor.invokeMethod(ON_CONSTRUCT, null);
    }

    @Override
    public void onJoinWorld(final Level level) {
        executor.invokeMethod(ON_JOIN_WORLD, null, level);
    }

    @Override
    public void update() {
        executor.invokeMethod(UPDATE, null);
    }

    @Override
    public Map<String, ComponentBlueprint<?, ?>> componentsToAttach() {
        return blueprint.value().components();
    }

    @Override
    public EntityPatch<Entity> getScriptOwner() {
        return blueprintOwner;
    }

    @Override
    public Holder.Reference<DataDrivenEntityPatchBlueprint> getBlueprint() {
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
