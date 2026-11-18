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
/// A blueprint laid over data parents runs as its [BlueprintBody#chain]: a method runs the graph of the nearest
/// body implementing it, and the bodies share one set of variables.
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

    /// The blueprint's body, then the body of each data parent up to the root.
    private final List<BlueprintBody.Link<?>> chain;

    /// [#chain] folded into one body.
    private final BlueprintBody body;

    private final ScriptVariables variables;

    /// The run sites whose graph threw.
    private final Set<ScriptCaller> failed = new HashSet<>();

    /// How many runs are open.
    private int depth;

    /// Runs `body` alone, on variables built from it.
    ///
    /// @throws IllegalArgumentException when a member variable shares its name with a local that a run binds
    public BlueprintExecutor(final DataDrivenScript<?, ?> script, final BlueprintLayout layout, final BlueprintBody body) {
        this(script, layout, List.of(new BlueprintBody.Link<>(script.getBlueprint(), body)));
    }

    /// Runs `body` alone, on `variables`.
    ///
    /// @throws IllegalArgumentException when a member variable shares its name with a local that a run binds
    public BlueprintExecutor(
        final DataDrivenScript<?, ?> script, final BlueprintLayout layout, final BlueprintBody body,
        final ScriptVariables variables
    ) {
        this(script, layout, List.of(new BlueprintBody.Link<>(script.getBlueprint(), body)), variables);
    }

    /// Runs `chain`, on variables built from its folded body.
    ///
    /// @throws IllegalArgumentException when a member variable shares its name with a local that a run binds
    public BlueprintExecutor(
        final DataDrivenScript<?, ?> script, final BlueprintLayout layout, final List<? extends BlueprintBody.Link<?>> chain
    ) {
        this(script, layout, chain, new ScriptVariables(BlueprintBody.fold(chain.stream().map(BlueprintBody.Link::body).toList()).variables()));
    }

    /// Runs `chain`, on `variables`.
    ///
    /// @throws IllegalArgumentException when a member variable shares its name with a local that a run binds
    public BlueprintExecutor(
        final DataDrivenScript<?, ?> script, final BlueprintLayout layout, final List<? extends BlueprintBody.Link<?>> chain,
        final ScriptVariables variables
    ) {
        this.script = script;
        this.layout = layout;
        this.chain = List.copyOf(chain);
        this.body = BlueprintBody.fold(chain.stream().map(BlueprintBody.Link::body).toList());
        this.variables = variables;

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

    /// The blueprint's body laid over its data parents.
    public BlueprintBody body() {
        return body;
    }

    /// Whether the blueprint's own graph implementing the method `name`, or its own function `name`, threw.
    public boolean hasFailed(final String name) {
        return failed.contains(ScriptCaller.of(script, name));
    }

    /// Runs the blueprint's implementation of the parent's method `name` with `arguments`.
    ///
    /// @param parent what a [ParentCallNode] of the root body calls, and what answers when no body implements
    ///               the method
    /// @return what the graph returned, or null when it returned nothing or threw
    /// @throws IllegalArgumentException when the layout has no method `name`
    public @Nullable Object invokeMethod(final String name, final @Nullable Supplier<?> parent, final Object... arguments) {
        BlueprintLayout.Signature signature = layout.method(name);

        if (signature == null) {
            throw new IllegalArgumentException(layout.provider().getName() + " has no blueprint method " + name);
        }

        return invokeMethod(0, signature, parent, arguments);
    }

    /// Runs the graph of the nearest body of [#chain] at or past `from` implementing `signature`, whose
    /// [ParentCallNode] runs the next one up. Answers `parent` when none implements it.
    private @Nullable Object invokeMethod(
        final int from, final BlueprintLayout.Signature signature, final @Nullable Supplier<?> parent, final Object[] arguments
    ) {
        for (int i = from; i < chain.size(); i++) {
            BlueprintBody.Link<?> link = chain.get(i);
            FlowNode graph = link.body().methods().get(signature.key());

            if (graph == null) continue;

            int next = i + 1;

            return run(
                new ScriptCaller(script.getScriptOwner(), link.blueprint().key(), signature.name()), graph, signature.parameterNames(), arguments,
                () -> invokeMethod(next, signature, parent, arguments));
        }

        return parent == null ? null : parent.get();
    }

    /// Runs the function `name` the blueprint declares, or inherits from a data parent, with `arguments`.
    ///
    /// @return what the function returned, or null when it returned nothing or threw
    /// @throws IllegalArgumentException when no body declares a function `name`
    public @Nullable Object callFunction(final String name, final Object... arguments) {
        if (!body.functions().containsKey(name)) {
            throw new IllegalArgumentException("The blueprint declares no function " + name);
        }

        return callFunction(0, name, arguments);
    }

    /// Runs the function `name` of the nearest body of [#chain] at or past `from` declaring it, whose
    /// [ParentCallNode] runs the next one up. Answers null when none declares it.
    private @Nullable Object callFunction(final int from, final String name, final Object[] arguments) {
        for (int i = from; i < chain.size(); i++) {
            BlueprintBody.Link<?> link = chain.get(i);
            FunctionGraph function = link.body().functions().get(name);

            if (function == null) continue;

            int next = i + 1;
            List<String> parameterNames = function.parameters().stream().map(FunctionGraph.Parameter::name).toList();

            return run(
                new ScriptCaller(script.getScriptOwner(), link.blueprint().key(), name), function.body(), parameterNames, arguments,
                () -> callFunction(next, name, arguments));
        }

        return null;
    }

    /// Runs `graph` as the run of the blueprint named `site`, binding `arguments` under `parameterNames`.
    ///
    /// @return the value a return node carried, else the value the graph itself answered, or null when the
    ///         graph threw or was latched off
    /// @throws IllegalStateException when runs nest deeper than [#MAX_DEPTH]
    public @Nullable Object run(
        final String site, final FlowNode graph, final List<String> parameterNames, final Object[] arguments,
        final @Nullable Supplier<?> parent
    ) {
        return run(ScriptCaller.of(script, site), graph, parameterNames, arguments, parent);
    }

    /// Runs `graph` as `caller`, binding `arguments` under `parameterNames`.
    private @Nullable Object run(
        final ScriptCaller caller, final FlowNode graph, final List<String> parameterNames, final Object[] arguments,
        final @Nullable Supplier<?> parent
    ) {
        if (failed.contains(caller)) return null;

        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Blueprint runs nested deeper than " + MAX_DEPTH + " at " + caller.site());
        }

        Map<String, Object> interrupted = variables.captureLocals();

        variables.reset();

        for (int i = 0; i < parameterNames.size() && i < arguments.length; i++) {
            variables.setValue(parameterNames.get(i), arguments[i]);
        }

        if (parent != null) variables.setValue(PARENT_LOCAL, parent);

        depth++;

        ScriptNode.ExecutionContext context = ScriptRecorder.newContext(caller, variables);

        try {
            Object answered = graph.execute(script, context);

            return context.isReturning() ? context.takeReturnedValue() : answered;
        } catch (Throwable throwable) {
            failed.add(caller);
            ScriptRecorder.recordFailure(context, throwable);

            return null;
        } finally {
            depth--;
            variables.restoreLocals(interrupted);
        }
    }
}
