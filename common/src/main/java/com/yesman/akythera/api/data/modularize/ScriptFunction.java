package com.yesman.akythera.api.data.modularize;

import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import org.jspecify.annotations.Nullable;

import java.util.function.*;

/// A global functional interface that is accommodatable any java methods within 5 parameters
///
/// Used in serializing logic scripts. Allows transforming "java code" to "json data pack".
///
/// [ScriptFunction] unifies all modular functions in one registry, but vulnerable for
/// type casting errors and invalid method signature calls. Logics must be built in AkytheraLabs
/// script editor where type and method signatures are verified.
///
/// Addon developers can register their modules either by class([ScriptModularizeHelper#scanScriptModulesAndRegister])
/// or by custom logics([ScriptModularizeHelper#makeCustomScript]).
@SuppressWarnings("unchecked")
public interface ScriptFunction {
    default <R> ScriptSupplier<R> castToSupplier() throws ClassCastException {
        return (ScriptSupplier<R>) this;
    }

    default ScriptRunnable castToRunnable() throws ClassCastException {
        return (ScriptRunnable) this;
    }

    default <P> ScriptConsumer<P> castToConsumer() throws ClassCastException {
        return (ScriptConsumer<P>) this;
    }

    default <R, P1> ScriptUniFunction<R, P1> castToUniFunction() throws ClassCastException {
        return (ScriptUniFunction<R, P1>) this;
    }

    default <P1, P2, R> ScriptBiFunction<P1, P2, R> castToBiFunction() throws ClassCastException {
        return (ScriptBiFunction<P1, P2, R>) this;
    }

    default <P1, P2, P3, R> ScriptTriFunction<P1, P2, P3, R> castToTriFunction() throws ClassCastException {
        return (ScriptTriFunction<P1, P2, P3, R>) this;
    }

    default <P1, P2, P3, P4, R> ScriptQuadFunction<P1, P2, P3, P4, R> castToQuadFunction() throws ClassCastException {
        return (ScriptQuadFunction<P1, P2, P3, P4, R>) this;
    }

    default <P1, P2, P3, P4, P5, R> ScriptQuintFunction<P1, P2, P3, P4, P5, R> castToQuintFunction() throws ClassCastException {
        return (ScriptQuintFunction<P1, P2, P3, P4, P5, R>) this;
    }

    default <P1, P2> ScriptBiConsumer<P1, P2> castToBiConsumer() throws ClassCastException {
        return (ScriptBiConsumer<P1, P2>) this;
    }

    default <P1, P2, P3> ScriptTriConsumer<P1, P2, P3> castToTriConsumer() throws ClassCastException {
        return (ScriptTriConsumer<P1, P2, P3>) this;
    }

    default <P1, P2, P3, P4> ScriptQuadConsumer<P1, P2, P3, P4> castToQuadConsumer() throws ClassCastException {
        return (ScriptQuadConsumer<P1, P2, P3, P4>) this;
    }

    default <P1, P2, P3, P4, P5> ScriptQuintConsumer<P1, P2, P3, P4, P5> castToQuintConsumer() throws ClassCastException {
        return (ScriptQuintConsumer<P1, P2, P3, P4, P5>) this;
    }

    default ScriptFunction downcast() {
        return this;
    }

    FunctionSignature functionSignature();

    Metadata metadata();

    /// Saying this function can be isolated from execution flow.
    default boolean isPure() {
        return metadata().pure();
    }

    /// Saying this function always accepts the first parameter as the class owner
    default boolean isMember() {
        return metadata().member();
    }

    record ScriptSupplier<R>(
        Supplier<R> supplier, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptRunnable(
        Runnable runnable, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction
    {
        @Override
        public boolean isPure() {
            return true;
        }

        @Override
        public boolean isMember() {
            return false;
        }
    }

    record ScriptConsumer<P>(
        Consumer<P> consumer, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptUniFunction<P, R>(
        Function<P, R> function, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptBiFunction<P1, P2, R>(
        BiFunction<P1, P2, R> biFunction, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptTriFunction<P1, P2, P3, R>(
        TriFunction<P1, P2, P3, R> triFunction, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptQuadFunction<P1, P2, P3, P4, R>(
        QuadFunction<P1, P2, P3, P4, R> quadFunction, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptQuintFunction<P1, P2, P3, P4, P5, R>(
        QuintFunction<P1, P2, P3, P4, P5, R> quintFunction, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptBiConsumer<P1, P2>(
        BiConsumer<P1, P2> biConsumer, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptTriConsumer<P1, P2, P3>(
        TriConsumer<P1, P2, P3> triConsumer, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptQuadConsumer<P1, P2, P3, P4>(
        QuadConsumer<P1, P2, P3, P4> quadConsumer, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    record ScriptQuintConsumer<P1, P2, P3, P4, P5>(
        QuintConsumer<P1, P2, P3, P4, P5> quintConsumer, FunctionSignature functionSignature, Metadata metadata
    ) implements ScriptFunction {
    }

    @FunctionalInterface
    interface TriFunction<P1, P2, P3, R> {
        R apply(P1 p1, P2 p2, P3 p3);
    }

    @FunctionalInterface
    interface QuadFunction<P1, P2, P3, P4, R> {
        R apply(P1 p1, P2 p2, P3 p3, P4 p4);
    }

    @FunctionalInterface
    interface QuintFunction<P1, P2, P3, P4, P5, R> {
        R apply(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5);
    }

    @FunctionalInterface
    interface TriConsumer<P1, P2, P3> {
        void accept(P1 p1, P2 p2, P3 p3);
    }

    @FunctionalInterface
    interface QuadConsumer<P1, P2, P3, P4> {
        void accept(P1 p1, P2 p2, P3 p3, P4 p4);
    }

    @FunctionalInterface
    interface QuintConsumer<P1, P2, P3, P4, P5> {
        void accept(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5);
    }

    record Metadata(boolean pure, boolean member, String label, @Nullable String category, @Nullable String description, Usage usage) {
    }

    enum Usage {
        SCRIPT, ANIM_GRAPH, PARTICLE_GENERATION, MOTION_WARPING
    }
}
