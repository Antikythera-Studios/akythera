package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;

public interface ScriptNode {
    /// Executes the logic
    ///
    /// @param <T> return type in result of executing this node
    ///            including `void` return. Return types are explicit
    ///            and unsafely cast because we always regard type-safety
    ///            is secured by either editor screen or builders.
    <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context);

    class ExecutionContext {
        private final ScriptVariables locals;
        private final boolean debug;
        private int depth;

        public ExecutionContext(final ScriptVariables locals, final boolean debug) {
            this.locals = locals;
            this.debug = debug;
        }

        public ScriptVariables locals() {
            return locals;
        }

        public void enter(final ScriptNode scriptNode) {
            if (debug) {
                Akythera.LOGGER.info("{}Called {}", getIndentation(), scriptNode.toString());
                depth++;
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T returnVal(final Object returnValue) {
            if (debug) {
                depth--;
                Akythera.LOGGER.info("{}Returned {}", getIndentation(), returnValue);
            }

            return (T)returnValue;
        }

        public <T> T done() {
            if (debug) {
                depth--;
                Akythera.LOGGER.info("{}Done", getIndentation());
            }

            return null;
        }

        private String getIndentation() {
            StringBuilder indent = new StringBuilder();
            indent.repeat("  ", depth);
            return indent.toString();
        }
    }
}
