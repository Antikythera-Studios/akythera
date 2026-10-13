package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Runs the graphs of a [DataDrivenWidgetBlueprint]: the container's events, and its widgets' events and bindings,
/// all over one set of variables.
public abstract class DataDrivenWidgetContainerScript implements WidgetContainerScript, DataDrivenScript<ComponentAttachable, DataDrivenWidgetBlueprint> {
    private final Holder.Reference<DataDrivenWidgetBlueprint> blueprint;

    private final ComponentAttachable container;

    protected final BlueprintExecutor executor;

    /// @param layoutProvider the script interface whose events the blueprint implements
    protected DataDrivenWidgetContainerScript(
        final Holder.Reference<DataDrivenWidgetBlueprint> blueprint,
        final ComponentAttachable container,
        final Class<?> layoutProvider
    ) {
        this.blueprint = blueprint;
        this.container = container;
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(layoutProvider), blueprint.value().body());
    }

    /// The callback running the widget event graph `key`, binding its arguments under `argumentNames`. Null when
    /// the blueprint holds no graph there.
    @Override
    public @Nullable Consumer<Object[]> widgetEvent(final String key, final List<String> argumentNames) {
        FlowNode graph = blueprint.value().widgetEvents().get(key);

        return graph == null ? null : arguments -> executor.run(key, graph, argumentNames, arguments, null);
    }

    /// The supplier answering the widget binding graph `key` each time it is asked. Null when the blueprint holds
    /// no graph there.
    @Override
    public @Nullable Supplier<@Nullable Object> widgetBinding(final String key) {
        FlowNode graph = blueprint.value().widgetBindings().get(key);

        return graph == null ? null : () -> executor.run(key, graph, List.of(), new Object[0], null);
    }

    @Override
    public Map<String, ComponentBlueprint<?, ?>> componentsToAttach() {
        return blueprint.value().components();
    }

    @Override
    public ComponentAttachable getScriptOwner() {
        return container;
    }

    @Override
    public Holder.Reference<DataDrivenWidgetBlueprint> getBlueprint() {
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
