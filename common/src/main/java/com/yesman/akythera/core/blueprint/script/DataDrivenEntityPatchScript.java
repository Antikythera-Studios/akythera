package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.DataDrivenEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.SourceCodeEntityPatchBlueprint;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Runs the [EntityPatchScript] events of a [DataDrivenEntityPatchBlueprint].
///
/// The blueprint is laid over its data parents, and over the source code script the nearest of them names:
/// [DataDrivenEntityPatchBlueprint#parentScript], or a data parent that is a [SourceCodeEntityPatchBlueprint].
/// An event no blueprint implements runs the source code script's, and so does a [ParentCallNode] of the root
/// blueprint. The components are gathered root first, a nearer blueprint replacing the ones it names again.
public final class DataDrivenEntityPatchScript
    implements EntityPatchScript, DataDrivenScript<EntityPatch<Entity>, DataDrivenEntityPatchBlueprint>
{
    /// [ScriptCaller] sites of the entry points, which are the events' method names.
    public static final String ON_CONSTRUCT = "onConstruct";
    public static final String ON_JOIN_WORLD = "onJoinWorld";
    public static final String UPDATE = "update";
    public static final String ON_POSSESSED = "onPossessed";
    public static final String ON_UNPOSSESSED = "onUnpossessed";
    public static final String ON_DEATH = "onDeath";

    /// Script blueprint
    private final Holder.Reference<DataDrivenEntityPatchBlueprint> blueprint;

    private final EntityPatch<Entity> blueprintOwner;

    private final BlueprintExecutor executor;

    /// The source code script the blueprints are laid over. Null when none names one.
    private final @Nullable EntityPatchScript parent;

    /// The components of the source code script and of every blueprint, by name.
    private final Map<String, ComponentBlueprint<?, ?>> components;

    @SuppressWarnings("unchecked")
    public DataDrivenEntityPatchScript(final Holder.Reference<DataDrivenEntityPatchBlueprint> blueprint, EntityPatch<Entity> blueprintOwner) {
        List<BlueprintBody.Link<EntityPatchBlueprint>> chain = BlueprintBody.chain((Holder.Reference<EntityPatchBlueprint>)(Holder<?>)blueprint);
        Map<String, ComponentBlueprint<?, ?>> gathered = new LinkedHashMap<>();
        EntityPatchScript sourceCodeScript = null;

        for (BlueprintBody.Link<EntityPatchBlueprint> link : chain.reversed()) {
            SourceCodeEntityPatchBlueprint sourceCode = switch (link.blueprint().value()) {
                case SourceCodeEntityPatchBlueprint entry -> entry;
                case DataDrivenEntityPatchBlueprint entry ->
                    entry.parentScript().map(script -> new SourceCodeEntityPatchBlueprint(entry.ctor(), script)).orElse(null);
            };

            if (sourceCode != null) {
                sourceCodeScript = sourceCode.instantiate(blueprintOwner);
                gathered.putAll(sourceCodeScript.componentsToAttach());
            }

            if (link.blueprint().value() instanceof DataDrivenEntityPatchBlueprint dataDriven) gathered.putAll(dataDriven.components());
        }

        this.blueprint = blueprint;
        this.blueprintOwner = blueprintOwner;
        this.parent = sourceCodeScript;
        this.components = Collections.unmodifiableMap(gathered);
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(EntityPatchScript.class), chain);
    }

    @Override
    public void onConstruct() {
        executor.invokeMethod(ON_CONSTRUCT, () -> {
            if (parent != null) parent.onConstruct();

            return null;
        });
    }

    @Override
    public void onJoinWorld(final Level level) {
        executor.invokeMethod(ON_JOIN_WORLD, () -> {
            if (parent != null) parent.onJoinWorld(level);

            return null;
        }, level);
    }

    @Override
    public void update() {
        executor.invokeMethod(UPDATE, () -> {
            if (parent != null) parent.update();

            return null;
        });
    }

    @Override
    public void onPossessed() {
        executor.invokeMethod(ON_POSSESSED, () -> {
            if (parent != null) parent.onPossessed();

            return null;
        });
    }

    @Override
    public void onUnpossessed() {
        executor.invokeMethod(ON_UNPOSSESSED, () -> {
            if (parent != null) parent.onUnpossessed();

            return null;
        });
    }

    @Override
    public void onDeath() {
        executor.invokeMethod(ON_DEATH, () -> {
            if (parent != null) parent.onDeath();

            return null;
        });
    }

    @Override
    public Map<String, ComponentBlueprint<?, ?>> componentsToAttach() {
        return components;
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
