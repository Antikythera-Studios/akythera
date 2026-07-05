package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;

import java.util.List;

/// Signature of [ScriptFunction]
public record FunctionSignature(List<String> parameterNames, List<Class<?>> parameters, Class<?> returnType) {
    /// Generate a function signature
    public static FunctionSignature create(Class<?> returnType, List<String> parameterNames, Class<?>... parameters) {
        return new FunctionSignature(parameterNames, List.of(parameters), returnType);
    }

    /// Compares function signature
    @Override
    public boolean equals(final Object other) {
        if (other instanceof FunctionSignature functionSignature) {
            if (parameters().size() != functionSignature.parameters().size()) {
                return false;
            }

            for (int i = 0; i < parameters().size(); i++) {
                if (!parameters().get(i).isAssignableFrom(functionSignature.parameters().get(i))) return false;
            }

            return true;
        }

        return false;
    }

    /// Compare if the given class type can be used as parameter of `index`
    ///
    /// Comparison is boxing-aware since reflected methods carry primitive types
    /// while custom scripts use wrappers. An [Object] provider is a dynamically
    /// typed value (e.g. a script variable) and is accepted in any slot.
    ///
    /// Numeric slots additionally accept any [Number]-driven provider (explicit-cast
    /// semantics, e.g. `Double` -> `float`): the synthesized SAM coerces the value
    /// through [Number] at the invocation boundary, so the conversion is guaranteed
    /// to succeed at runtime.
    public boolean isParameterCompatible(Class<?> type, int index) {
        Class<?> provided = boxed(type);
        Class<?> expected = boxed(parameters().get(index));

        if (provided == Object.class || expected.isAssignableFrom(provided)) {
            return true;
        }

        return isNumeric(expected) && isNumeric(provided);
    }

    private static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(type);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive())   return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class)    return Byte.class;
        if (type == short.class)   return Short.class;
        if (type == char.class)    return Character.class;
        if (type == int.class)     return Integer.class;
        if (type == long.class)    return Long.class;
        if (type == float.class)   return Float.class;
        if (type == double.class)  return Double.class;
        return Void.class;
    }
}
