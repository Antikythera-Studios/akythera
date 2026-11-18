package com.yesman.akythera.client.api.gui.script;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.SourceCodeWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Runs the graphs of a [DataDrivenWidgetBlueprint]: the container's events, and its widgets' events and bindings,
/// all over one set of variables.
///
/// The blueprint is laid over its data parents, and over the source code script the nearest of them names:
/// [DataDrivenWidgetBlueprint#parentScript], or a data parent that is a [SourceCodeWidgetBlueprint]. An event, a
/// widget event or a widget binding no blueprint implements is the source code script's. The widgets are gathered
/// root first, a nearer blueprint replacing the ones it names again.
public abstract class DataDrivenWidgetContainerScript implements WidgetContainerScript, DataDrivenScript<ComponentAttachable, DataDrivenWidgetBlueprint> {
    private final Holder.Reference<DataDrivenWidgetBlueprint> blueprint;

    private final ComponentAttachable container;

    protected final BlueprintExecutor executor;

    /// The source code script the blueprints are laid over. Null when none names one.
    protected final @Nullable WidgetContainerScript parent;

    /// The blueprint, then each data-driven data parent up to the root.
    private final List<DataDrivenWidgetBlueprint> lineage;

    /// The widgets of the source code script and of every blueprint, by name.
    private final Map<String, ComponentBlueprint<?, ?>> components;

    /// @param layoutProvider the script interface whose events the blueprint implements
    @SuppressWarnings("unchecked")
    protected DataDrivenWidgetContainerScript(
        final Holder.Reference<DataDrivenWidgetBlueprint> blueprint,
        final ComponentAttachable container,
        final Class<?> layoutProvider
    ) {
        List<BlueprintBody.Link<WidgetBlueprint>> chain = BlueprintBody.chain((Holder.Reference<WidgetBlueprint>)(Holder<?>)blueprint);
        List<DataDrivenWidgetBlueprint> dataDriven = new ArrayList<>();
        Map<String, ComponentBlueprint<?, ?>> gathered = new LinkedHashMap<>();
        WidgetContainerScript sourceCodeScript = null;

        for (BlueprintBody.Link<WidgetBlueprint> link : chain.reversed()) {
            SourceCodeWidgetBlueprint sourceCode = switch (link.blueprint().value()) {
                case SourceCodeWidgetBlueprint entry -> entry;
                case DataDrivenWidgetBlueprint entry ->
                    entry.parentScript().map(script -> new SourceCodeWidgetBlueprint(entry.usage(), entry.ctor(), entry.title(), script)).orElse(null);
            };

            if (sourceCode != null) {
                sourceCodeScript = sourceCode.instantiate(container);
                gathered.putAll(sourceCodeScript.componentsToAttach());
            }

            if (link.blueprint().value() instanceof DataDrivenWidgetBlueprint entry) {
                dataDriven.addFirst(entry);
                gathered.putAll(entry.components());
            }
        }

        this.blueprint = blueprint;
        this.container = container;
        this.parent = sourceCodeScript;
        this.lineage = List.copyOf(dataDriven);
        this.components = Collections.unmodifiableMap(gathered);
        this.executor = new BlueprintExecutor(this, ScriptModularizeHelper.scanBlueprintLayout(layoutProvider), chain);
    }

    /// The callback running the widget event graph `key` of the nearest blueprint holding one, binding its
    /// arguments under `argumentNames`. The source code script's when no blueprint holds one.
    @Override
    public @Nullable Consumer<Object[]> widgetEvent(final String key, final List<String> argumentNames) {
        for (DataDrivenWidgetBlueprint entry : lineage) {
            FlowNode graph = entry.widgetEvents().get(key);

            if (graph != null) return arguments -> executor.run(key, graph, argumentNames, arguments, null);
        }

        return parent == null ? null : parent.widgetEvent(key, argumentNames);
    }

    /// The supplier answering the widget binding graph `key` of the nearest blueprint holding one, each time it is
    /// asked. The source code script's when no blueprint holds one.
    @Override
    public @Nullable Supplier<@Nullable Object> widgetBinding(final String key) {
        for (DataDrivenWidgetBlueprint entry : lineage) {
            FlowNode graph = entry.widgetBindings().get(key);

            if (graph != null) return () -> executor.run(key, graph, List.of(), new Object[0], null);
        }

        return parent == null ? null : parent.widgetBinding(key);
    }

    @Override
    public Map<String, ComponentBlueprint<?, ?>> componentsToAttach() {
        return components;
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
