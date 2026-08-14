package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// One node execution inside an [ExecutionRecord]: what ran, what it was handed, what it answered, and
/// what it ran in turn.
public final class CallStack {
    /// How the frame ended. [#RUNNING] survives into a finished record when the script threw partway
    /// through, which is what points at the node that died.
    public enum Outcome {
        RUNNING, RETURNED, VOID
    }

    private final ScriptNode node;
    private final List<CallStack> children = new ArrayList<>();

    private List<@Nullable Object> inputs = List.of();
    private @Nullable Object returnValue;
    private Outcome outcome = Outcome.RUNNING;

    CallStack(final ScriptNode node) {
        this.node = node;
    }

    public ScriptNode node() {
        return node;
    }

    /// [ScriptNode#nodeId] of the node this frame ran.
    public int nodeId() {
        return node.nodeId();
    }

    public List<CallStack> children() {
        return Collections.unmodifiableList(children);
    }

    /// The argument values the node resolved before it ran. Empty for every node that takes none.
    public List<@Nullable Object> inputs() {
        return inputs;
    }

    public @Nullable Object returnValue() {
        return returnValue;
    }

    public Outcome outcome() {
        return outcome;
    }

    /// The function this frame ran, or null when the node is not a call — a literal, a branch, a
    /// sequence. Derived from [#node] so it cannot drift away from it.
    public @Nullable ScriptFunction function() {
        return node instanceof FunctionNode functionNode ? functionNode.function() : null;
    }

    /// Signature of [#function], or null when this frame is not a call.
    public @Nullable FunctionSignature signature() {
        ScriptFunction scriptFunction = function();
        return scriptFunction == null ? null : scriptFunction.functionSignature();
    }

    void addChild(final CallStack child) {
        children.add(child);
    }

    void setInputs(final Object[] inputs) {
        // Not List.of: script arguments are legitimately null, and List.of rejects nulls.
        this.inputs = Collections.unmodifiableList(Arrays.asList(inputs.clone()));
    }

    void close(final @Nullable Object returnValue, final Outcome outcome) {
        this.returnValue = returnValue;
        this.outcome = outcome;
    }
}