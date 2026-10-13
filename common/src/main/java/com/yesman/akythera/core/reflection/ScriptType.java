package com.yesman.akythera.core.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a class whose values a script may hold, in a variable or on a node pin.
///
/// [ScriptModularizeHelper#scanScriptModulesAndRegister] registers it as a plain
/// [com.yesman.akythera.api.data.modularize.ScriptType], with no codec and no implicit casts, under the class's simple
/// name in snake case.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScriptType {
    /// Whether the scan registers the type. False marks a class whose type is registered by hand, with the codec,
    /// the implicit casts or the registry key the scan does not give.
    boolean autoRegister() default true;
}
