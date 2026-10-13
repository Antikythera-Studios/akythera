package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A field every graph of a blueprint reads and writes, listed in the Variables panel of the blueprint
/// editor.
///
/// The field must be neither `final`, `static` nor `private`, and its type must be registered as a
/// [ScriptType]. Scanned by [ScriptModularizeHelper#scanBlueprintLayout].
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BlueprintField {
}
