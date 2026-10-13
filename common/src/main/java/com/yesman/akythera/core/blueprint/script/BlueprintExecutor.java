package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.core.reflection.BlueprintFunction;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/// Runs the graphs of one [DataDrivenScript]'s blueprint: the implementations of its parent's
/// [BlueprintFunction] methods, and the functions the blueprint declares.
///
/// A run binds its arguments as locals under the parameter names. A run started inside another one sets the
/// other's locals aside and puts them back when it ends. A graph that throws is recorded to
/// [ScriptRecorder] and is not run again by this executor.
public final class BlueprintExecutor {
    /// The local a method's run holds the parent implementation under, which a [ParentCallNode] calls.
    public static final String PARENT_LOCAL = "super";

    /// How deep runs may nest before the innermost one is refused.
    private static final int MAX_DEPTH = 64;

    private final DataDrivenScript<?, ?> script;
    private final BlueprintLayout layout;
    private final BlueprintBody body;
    private final ScriptVariables variables;

    /// Names of the methods and functions whose graph threw.
    private final Set<String> failed = new HashSet<>();

    /// How many runs are open.
    private int depth;

    /// @throws IllegalArgumentException when a member variable shares its name with a local that a run binds
    public BlueprintExecutor(final DataDrivenScript<?, ?> script, final BlueprintLayout layout, final BlueprintBody body) {
        this.script = script;
        this.layout = layout;
        this.body = body;
        this.variables = new ScriptVariables(body.variables());

        Set<String> bound = new HashSet<>();
        bound.add(PARENT_LOCAL);
        layout.events().forEach(event -> bound.addAll(event.parameterNames()));
        layout.functions().forEach(function -> bound.addAll(function.parameterNames()));
        body.functions().values().forEach(function ->
            function.parameters().forEach(parameter -> bound.add(parameter.name())));

        for (ScriptVariables.VariableInfo variable : body.variables()) {
            if (bound.contains(variable.name())) {
                throw new IllegalArgumentException(
                    "A blueprint variable may not be named " + variable.name() + ", which a graph run binds.");
            }
        }
    }

    public ScriptVariables variables() {
        return variables;
    }

    /// Whether the graph implementing the method `name`, or the function `name`, threw.
    public boolean hasFailed(final String name) {
        return failed.contains(name);
    }

    /// Runs the blueprint's implementation of the parent's method `name` with `arguments`.
    ///
    /// @param parent what a [ParentCallNode] calls, and what answers when the blueprint does not implement
    ///               the method
    /// @return what the graph returned, or null when it returned nothing or threw
    /// @throws IllegalArgumentException when the layout has no method `name`
    public @Nullable Object invokeMethod(final String name, final @Nullable Supplier<?> parent, final Object... arguments) {
        BlueprintLayout.Signature signature = layout.method(name);

        if (signature == null) {
            throw new IllegalArgumentException(layout.provider().getName() + " has no blueprint method " + name);
        }

        FlowNode graph = body.methods().get(signature.key());

        if (graph == null) return parent == null ? null : parent.get();

        return run(name, graph, signature.parameterNames(), arguments, parent);
    }

    /// Runs the function `name` the blueprint declares with `arguments`.
    ///
    /// @return what the function returned, or null when it returned nothing or threw
    /// @throws IllegalArgumentException when the blueprint declares no function `name`
    public @Nullable Object callFunction(final String name, final Object... arguments) {
        FunctionGraph function = body.functions().get(name);

        if (function == null) throw new IllegalArgumentException("The blueprint declares no function " + name);

        List<String> parameterNames = function.parameters().stream().map(FunctionGraph.Parameter::name).toList();

        return run(name, function.body(), parameterNames, arguments, null);
    }

    /// Runs `graph` as the run named `site`, binding `arguments` under `parameterNames`.
    ///
    /// @return the value a return node carried, else the value the graph itself answered, or null when the
    ///         graph threw or was latched off
    /// @throws IllegalStateException when runs nest deeper than [#MAX_DEPTH]
    public @Nullable Object run(
        final String site, final FlowNode graph, final List<String> parameterNames, final Object[] arguments,
        final @Nullable Supplier<?> parent
    ) {
        if (failed.contains(site)) return null;

        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Blueprint runs nested deeper than " + MAX_DEPTH + " at " + site);
        }

        Map<String, Object> interrupted = variables.captureLocals();

        variables.reset();

        for (int i = 0; i < parameterNames.size() && i < arguments.length; i++) {
            variables.setValue(parameterNames.get(i), arguments[i]);
        }

        if (parent != null) variables.setValue(PARENT_LOCAL, parent);

        depth++;

        ScriptNode.ExecutionContext context = ScriptRecorder.newContext(ScriptCaller.of(script, site), variables);

        try {
            Object answered = graph.execute(script, context);

            return context.isReturning() ? context.takeReturnedValue() : answered;
        } catch (Throwable throwable) {
            failed.add(site);
            ScriptRecorder.recordFailure(context, throwable);

            return null;
        } finally {
            depth--;
            variables.restoreLocals(interrupted);
        }
    }
}
