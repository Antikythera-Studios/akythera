package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.util.helper.ParseUtils;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

/// Signature of [ScriptFunction]
///
/// @param parameters        the class each parameter erases to
/// @param genericParameters each parameter as declared, e.g. `ResourceKey<AnimationMontage>`. The same as
///                          [#parameters] for a parameter declared without type arguments
/// @param genericReturnType the return type as declared, e.g. `V` of `Map<K, V>`. The same as [#returnType]
///                          for a return type declared without type arguments
public record FunctionSignature(
    List<String> parameterNames, List<Class<?>> parameters, Class<?> returnType, List<Type> genericParameters, Type genericReturnType
) {
    /// Generate a function signature
    public static FunctionSignature create(Class<?> returnType, List<String> parameterNames, Class<?>... parameters) {
        return new FunctionSignature(parameterNames, List.of(parameters), returnType, List.of(parameters), returnType);
    }

    /// Generate a function signature whose return type and parameters may carry type arguments or be type
    /// variables, see [ParseUtils#parameterized].
    public static FunctionSignature create(Type returnType, List<String> parameterNames, Type... parameters) {
        List<Class<?>> erased = Arrays.stream(parameters).<Class<?>>map(ParseUtils::erase).toList();

        return new FunctionSignature(parameterNames, erased, ParseUtils.erase(returnType), List.of(parameters), returnType);
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
