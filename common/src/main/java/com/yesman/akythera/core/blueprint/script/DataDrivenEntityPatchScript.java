package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;

public final class DataDrivenEntityPatchScript
    implements EntityPatchScript, DataDrivenScript<EntityPatch<Entity>, EntityPatchBlueprint>
{
    /// Script blueprint
    private final Holder.Reference<EntityPatchBlueprint> blueprint;

    private final EntityPatch<Entity> blueprintOwner;

    /// Script variables
    private final ScriptVariables variables;

    public DataDrivenEntityPatchScript(final Holder.Reference<EntityPatchBlueprint> blueprint, EntityPatch<Entity> blueprintOwner) {
        this.blueprint = blueprint;
        this.blueprintOwner = blueprintOwner;
        this.variables = new ScriptVariables(blueprint.value().variables());
    }

    @Override
    public void onConstruct() {
        variables.reset();
        getBlueprint().value().onConstruct().execute(this, new ScriptNode.ExecutionContext(variables));
    }

    @Override
    public void onJoinWorld(final Level level) {
        variables.reset();
        variables.setValue("level", level);

        getBlueprint().value().onJoinWorld().execute(this, new ScriptNode.ExecutionContext(variables));
    }

    @Override
    public void update() {
        variables.reset();

        getBlueprint().value().update().execute(this, new ScriptNode.ExecutionContext(variables));
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
    public Holder.Reference<EntityPatchBlueprint> getBlueprint() {
        return blueprint;
    }

    @Override
    public ScriptVariables getScriptVariable() {
        return variables;
    }
}
