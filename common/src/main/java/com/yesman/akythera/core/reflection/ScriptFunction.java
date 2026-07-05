package com.yesman.akythera.core.reflection;

import io.netty.util.internal.StringUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/// Annotate fields that you want to expose as [com.yesman.akythera.api.data.modularize.ScriptFunction].
///
/// Developers must call [ScriptModularizeHelper#scanScriptModulesAndRegister]
/// to register fields
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScriptFunction {
    /// Whether the function can be executed without execution flow.
    ///
    /// Meaning that the node in AkytheraLabs's script editor doesn't
    /// require execution pin.
    ///
    /// Can be [Supplier], or other [com.yesman.akythera.api.data.modularize.ScriptFunction] where the
    /// first parameter is always the object owner.
    boolean pure() default false;

    /// Visual name in script editor. Transform to pascal case if not specified.
    String label() default StringUtil.EMPTY_STRING;

    /// A categorized name of the function displayed on script editor. Folded by "/" separator.
    String category() default StringUtil.EMPTY_STRING;

    /// Description of the function.
    String desc() default StringUtil.EMPTY_STRING;

    /// Purpose of where the function will be used
    com.yesman.akythera.api.data.modularize.ScriptFunction.Usage usage() default com.yesman.akythera.api.data.modularize.ScriptFunction.Usage.SCRIPT;
}
