package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/// A method a blueprint implements, overrides or sets, by [#graphType]:
///
/// - [ScriptFunction.BlueprintGraphType#EVENT_GRAPH]: an entry node on the Event Graph. Returns `void`.
/// - [ScriptFunction.BlueprintGraphType#FUNCTION]: an override the Functions panel offers. Any signature.
/// - [ScriptFunction.BlueprintGraphType#PROPERTY]: a Details row. A [Supplier] shape whose return type is
///   registered as a [ScriptType].
///
/// The method must be neither `static` nor `private`. Scanned by
/// [ScriptModularizeHelper#scanBlueprintLayout].
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BlueprintFunction {
    ScriptFunction.BlueprintGraphType graphType();
}
