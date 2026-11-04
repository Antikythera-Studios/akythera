package com.yesman.akythera.api.data.modularize.node;

import org.jspecify.annotations.Nullable;

/// A callback a script hands to Java code. A [CallbackAdapterNode] builds one around a sub-flow.
@FunctionalInterface
public interface ScriptCallback extends Runnable {
    /// Runs with `self` answering every [SelfNode], or as [#run] when `self` is null. A callback written in
    /// Java has no Self node, and ignores it.
    default void runAs(final @Nullable Object self) {
        run();
    }
}
