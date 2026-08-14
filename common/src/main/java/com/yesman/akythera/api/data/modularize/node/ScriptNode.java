package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public interface ScriptNode {
    /// [#nodeId] of a node no editor row produced — a compiler-generated container, a shared
    /// singleton, or a tree built by hand.
    int NO_NODE_ID = -1;

    /// Script node graph identifier determined in editor side to debug the node graph.
    ///
    /// [#NO_NODE_ID] when no editor row produced this node. Ids are assigned per export and are only
    /// meaningful against the graph snapshot that produced this node's datapack — a consumer must
    /// verify before trusting one.
    default int nodeId() {
        return NO_NODE_ID;
    }

    /// Runs the node
    ///
    /// @param <T>     return type in result of executing this node
    ///                including `void` return. Return types are explicit
    ///                and unsafely cast because we always regard type-safety
    ///                is secured by either editor screen or builders.
    ///
    /// @param script  An owner of this script node. Could be null if the script
    ///                is run in headless, but the node must be a [PureNode].
    ///
    /// @param context A status of local, member variables when the node is being executed.
    ///                member variables keep its changes after the node execution is finished
    ///                while local variables are volatile and be reset in the next execution.
    <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context);

    class ExecutionContext {
        private final ScriptVariables locals;

        /// Where node executions are written, or null to run unrecorded.
        private final @Nullable ExecutionRecord record;

        /// Frames entered but not yet closed, innermost last.
        private final Deque<CallStack> openFrames = new ArrayDeque<>();

        /// A context that runs without recording.
        public ExecutionContext(final ScriptVariables locals) {
            this(locals, null);
        }

        public ExecutionContext(final ScriptVariables locals, final @Nullable ExecutionRecord record) {
            this.locals = locals;
            this.record = record;
        }

        public ScriptVariables locals() {
            return locals;
        }

        /// The record this context writes to, or null when it is not recording.
        public @Nullable ExecutionRecord record() {
            return record;
        }

        public void enter(final ScriptNode scriptNode) {
            if (record == null) return;

            CallStack frame = new CallStack(scriptNode);
            CallStack parent = openFrames.peekLast();

            if (parent == null) {
                record.addRoot(frame);
            } else {
                parent.addChild(frame);
            }

            openFrames.addLast(frame);
        }

        /// Attaches the argument values the open frame resolved before running.
        public void recordInputs(final Object... inputs) {
            if (record == null || inputs.length == 0) return;

            CallStack frame = openFrames.peekLast();

            if (frame != null) {
                frame.setInputs(inputs);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T returnVal(final Object returnValue) {
            closeFrame(returnValue, CallStack.Outcome.RETURNED);

            return (T)returnValue;
        }

        public <T> T done() {
            closeFrame(null, CallStack.Outcome.VOID);

            return null;
        }

        private void closeFrame(final @Nullable Object returnValue, final CallStack.Outcome outcome) {
            if (record == null) return;

            CallStack frame = openFrames.pollLast();

            if (frame != null) {
                frame.close(returnValue, outcome);
            }
        }
    }
}