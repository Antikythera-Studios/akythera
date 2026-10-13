package com.yesman.akythera.api.data.modularize.node;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The node executions of one script run, in the order they were entered.
///
/// Filled in place by the [ScriptNode.ExecutionContext] it was handed to, so whoever created the record
/// holds the finished tree as soon as `execute` returns.
///
/// There is one root per statement the run entered at the top level — a chained
/// [FlowFunctionNode#nextNode()] closes the frame before it, so a chain is a run of roots rather than a
/// nest.
public final class ExecutionRecord {
    private final List<CallStack> roots = new ArrayList<>();

    /// The throw that ended the run, or null while it has not thrown.
    private @Nullable ScriptFailure failure;

    public List<CallStack> roots() {
        return Collections.unmodifiableList(roots);
    }

    public @Nullable ScriptFailure failure() {
        return failure;
    }

    public boolean isEmpty() {
        return roots.isEmpty();
    }

    void addRoot(final CallStack root) {
        roots.add(root);
    }

    void setFailure(final ScriptFailure failure) {
        this.failure = failure;
    }

    /// Renders the run the way the inline debug logging used to, one `Called` / `Returned` pair per
    /// frame at a two-space indent, with the argument values each call resolved appended to its
    /// closing line.
    public String format() {
        StringBuilder out = new StringBuilder();

        for (CallStack root : roots) {
            append(out, root, 0);
        }

        if (failure != null) {
            out.append("Threw ").append(failure.cause()).append(" in ").append(failure.node()).append('\n');
        }

        return out.toString();
    }

    private static void append(final StringBuilder out, final CallStack frame, final int depth) {
        String indent = "  ".repeat(depth);

        out.append(indent).append("Called ").append(frame.node());

        // The row the node came from, which is what an editor matches a frame against. `no id` is the
        // reading that says the pack was built without them.
        out.append(frame.nodeId() == ScriptNode.NO_NODE_ID ? " (no id)" : " (#" + frame.nodeId() + ')').append('\n');

        for (CallStack child : frame.children()) {
            append(out, child, depth + 1);
        }

        out.append(indent).append(
            switch (frame.outcome()) {
                case RETURNED -> "Returned " + frame.returnValue();
                case VOID -> "Done";
                case RUNNING -> "Unfinished — execution left this node early";
            }
        );

        if (!frame.inputs().isEmpty()) {
            out.append(" with ").append(frame.inputs());
        }

        out.append('\n');
    }
}