package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public interface ScriptNode {
    /// [#nodeId] of a node no editor row produced — a compiler-generated container, a shared
    /// singleton, or a tree built by hand.
    int NO_NODE_ID = -1;

    /// Canvas key of a sub-tree no editor canvas produced — a hand-assembled tree, or a pack built
    /// without them.
    ///
    /// Row ids are numbered per canvas, so a sub-tree whose evaluation outlives the entry point that
    /// built it names the canvas its rows were numbered against as well as the row.
    String NO_CANVAS = "";

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

    /// The state of one run. Built by [ScriptRecorder], which is what every run site asks for one.
    class ExecutionContext {
        private final ScriptCaller caller;

        private final ScriptVariables locals;

        /// Where node executions are written, or null to run unrecorded.
        private final @Nullable ExecutionRecord record;

        /// Frames entered but not yet closed, innermost last.
        private final Deque<CallStack> openFrames = new ArrayDeque<>();

        /// Nodes entered but not yet closed, innermost last. Kept whether or not this context records,
        /// so a throw can name the node it left.
        private final Deque<ScriptNode> openNodes = new ArrayDeque<>();

        /// Whether a [ReturnNode] has fired, which stops every flow still unwinding.
        private boolean returning;

        /// What that [ReturnNode] carried.
        private @Nullable Object returnedValue;

        ExecutionContext(final ScriptCaller caller, final ScriptVariables locals, final @Nullable ExecutionRecord record) {
            this.caller = caller;
            this.locals = locals;
            this.record = record;
        }

        public ScriptCaller caller() {
            return caller;
        }

        public ScriptVariables locals() {
            return locals;
        }

        /// The record this context writes to, or null when it is not recording.
        public @Nullable ExecutionRecord record() {
            return record;
        }

        /// One snapshot of the locals, shared by every callback adapter in the open scope, so a task
        /// firing several callbacks over its life accumulates state across them.
        private @Nullable ScriptVariables callbackLocals;

        /// The locals a callback in the open scope reads, snapshotted on first ask.
        public ScriptVariables callbackLocals() {
            if (callbackLocals == null) callbackLocals = locals.snapshot();

            return callbackLocals;
        }

        /// Opens a scope whose callback adapters share one snapshot, and returns the enclosing one.
        ///
        /// A [FunctionNode] opens one around its parameters, so the adapters feeding one call share
        /// a snapshot and a second call in the same flow gets its own.
        public @Nullable ScriptVariables beginCallbackScope() {
            ScriptVariables enclosing = callbackLocals;

            callbackLocals = null;

            return enclosing;
        }

        public void endCallbackScope(final @Nullable ScriptVariables enclosing) {
            callbackLocals = enclosing;
        }

        /// Stops the flow and hands `value` to whoever ran it.
        public void signalReturn(final @Nullable Object value) {
            returning = true;
            returnedValue = value;
        }

        /// Whether a [ReturnNode] has fired. Every flow node checks this before running its next
        /// block.
        public boolean isReturning() {
            return returning;
        }

        /// The value a [ReturnNode] carried, clearing the signal so this context can run again.
        @SuppressWarnings("unchecked")
        public <T> @Nullable T takeReturnedValue() {
            Object value = returnedValue;

            returning = false;
            returnedValue = null;

            return (T) value;
        }

        public void enter(final ScriptNode scriptNode) {
            openNodes.addLast(scriptNode);

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
            openNodes.pollLast();

            if (record == null) return;

            CallStack frame = openFrames.pollLast();

            if (frame != null) {
                frame.close(returnValue, outcome);
            }
        }

        /// Captures the nodes still open as the failure `cause` left, writes it to [#record], and
        /// closes them.
        ScriptFailure fail(final Throwable cause) {
            ScriptFailure failure = new ScriptFailure(caller, List.copyOf(openNodes), cause);

            openNodes.clear();
            openFrames.clear();

            if (record != null) {
                record.setFailure(failure);
            }

            return failure;
        }
    }
}