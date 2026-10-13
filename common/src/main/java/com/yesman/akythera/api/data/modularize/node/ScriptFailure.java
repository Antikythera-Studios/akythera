package com.yesman.akythera.api.data.modularize.node;

import org.jspecify.annotations.Nullable;

import java.util.List;

/// A throw out of a node tree: who ran it, the nodes still open when it happened (outermost first), and
/// what was thrown.
public record ScriptFailure(ScriptCaller caller, List<ScriptNode> path, Throwable cause) {
    /// The innermost node open at the throw, or null when the run threw before entering any.
    public @Nullable ScriptNode node() {
        return path.isEmpty() ? null : path.getLast();
    }
}
