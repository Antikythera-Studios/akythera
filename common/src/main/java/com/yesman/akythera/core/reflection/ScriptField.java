package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import io.netty.util.internal.StringUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotate fields that you want to expose as [ScriptFunction]
///
/// This automatically creates getter/setter of annotated fields.
///
/// Developers must call [ScriptModularizeHelper#scanScriptModulesAndRegister]
/// to register fields
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ScriptField {
    /// Whether to create getter field
    boolean makeGetter() default true;

    /// Whether to create setter field
    boolean makeSetter() default true;

    /// A categorized name of the function displayed on script editor
    String category() default StringUtil.EMPTY_STRING;

    /// Description of the function.
    String desc() default StringUtil.EMPTY_STRING;
}
