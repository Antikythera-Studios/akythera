package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The members of a class that lay out its blueprint editor: its [BlueprintField]s and [BlueprintFunction]s,
/// gathered from the class, its superclasses and its interfaces.
///
/// Members come root class first, each class's in name order. A member overridden down the hierarchy keeps
/// its most derived annotated declaration, in the place its root declaration takes.
///
/// @param provider   the scanned class
/// @param fields     the Variables panel's inherited rows
/// @param properties the Details panel's rows
/// @param functions  the methods the Functions panel offers to override
/// @param events     the Event Graph's entry nodes
public record BlueprintLayout(
    Class<?> provider,
    List<Variable> fields,
    List<Property> properties,
    List<Signature> functions,
    List<Signature> events
) {
    public @Nullable Variable field(final String name) {
        for (Variable field : fields) {
            if (field.name().equals(name)) return field;
        }

        return null;
    }

    public @Nullable Property property(final String name) {
        for (Property property : properties) {
            if (property.name().equals(name)) return property;
        }

        return null;
    }

    /// The event or the function named `name`.
    public @Nullable Signature method(final String name) {
        for (Signature event : events) {
            if (event.name().equals(name)) return event;
        }

        for (Signature function : functions) {
            if (function.name().equals(name)) return function;
        }

        return null;
    }

    /// Gathers `provider`'s annotated members.
    ///
    /// @throws IllegalStateException when a member breaks its annotation's contract, or two members of one
    ///                               kind share a name
    public static BlueprintLayout scan(final Class<?> provider) {
        List<Class<?>> derivedFirst = new ArrayList<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(provider));

        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();

            if (type == Object.class || derivedFirst.contains(type)) continue;

            derivedFirst.add(type);

            if (type.getSuperclass() != null) pending.add(type.getSuperclass());

            pending.addAll(List.of(type.getInterfaces()));
        }

        Map<String, Field> fields = new LinkedHashMap<>();
        Map<String, Method> properties = new LinkedHashMap<>();
        Map<String, Method> functions = new LinkedHashMap<>();
        Map<String, Method> events = new LinkedHashMap<>();

        for (Class<?> type : derivedFirst.reversed()) {
            Field[] declaredFields = type.getDeclaredFields();
            Method[] declaredMethods = type.getDeclaredMethods();

            Arrays.sort(declaredFields, Comparator.comparing(Field::getName));
            Arrays.sort(declaredMethods, Comparator.comparing(Method::getName));

            for (Field field : declaredFields) {
                if (field.isAnnotationPresent(BlueprintField.class)) fields.put(field.getName(), field);
            }

            for (Method method : declaredMethods) {
                BlueprintFunction annotation = method.getAnnotation(BlueprintFunction.class);

                if (annotation == null || method.isSynthetic() || method.isBridge()) continue;

                String key = method.getName() + Arrays.toString(method.getParameterTypes());

                switch (annotation.graphType()) {
                    case PROPERTY -> properties.put(key, method);
                    case FUNCTION -> functions.put(key, method);
                    case EVENT_GRAPH -> events.put(key, method);
                }
            }
        }

        List<Variable> fieldMembers = new ArrayList<>();

        for (Field field : fields.values()) {
            int modifiers = field.getModifiers();
            ScriptType type = ScriptType.of(field.getGenericType());

            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers)) {
                throw new IllegalStateException(
                    "@BlueprintField " + describe(field) + " must be neither final, static nor private");
            }

            if (type == null) {
                throw new IllegalStateException(
                    "@BlueprintField " + describe(field) + " is of " + field.getGenericType().getTypeName()
                        + ", which is no registered ScriptType");
            }

            field.setAccessible(true);
            fieldMembers.add(new Variable(field, type));
        }

        List<Property> propertyMembers = new ArrayList<>();

        for (Method getter : properties.values()) {
            Signature signature = signatureOf(getter, ScriptFunction.BlueprintGraphType.PROPERTY);
            ScriptType type = ScriptType.of(getter.getGenericReturnType());

            if (getter.getParameterCount() != 0 || !signature.returnsValue()) {
                throw new IllegalStateException(
                    "PROPERTY " + describe(getter) + " must take no parameter and return a value");
            }

            if (type == null) {
                throw new IllegalStateException(
                    "PROPERTY " + describe(getter) + " returns " + getter.getGenericReturnType().getTypeName()
                        + ", which is no registered ScriptType");
            }

            String name = getter.getName();

            for (String prefix : List.of("get", "is")) {
                if (name.length() > prefix.length() && name.startsWith(prefix)
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                    name = Character.toLowerCase(name.charAt(prefix.length())) + name.substring(prefix.length() + 1);
                    break;
                }
            }

            propertyMembers.add(new Property(signature, name, ParseUtils.camelToSnake(name), type));
        }

        List<Signature> eventMembers = new ArrayList<>();

        for (Method event : events.values()) {
            Signature signature = signatureOf(event, ScriptFunction.BlueprintGraphType.EVENT_GRAPH);

            if (signature.returnsValue()) {
                throw new IllegalStateException("EVENT_GRAPH " + describe(event) + " must return void");
            }

            eventMembers.add(signature);
        }

        List<Signature> functionMembers = new ArrayList<>();

        for (Method function : functions.values()) {
            functionMembers.add(signatureOf(function, ScriptFunction.BlueprintGraphType.FUNCTION));
        }

        Set<String> names = new HashSet<>();

        for (Variable field : fieldMembers) {
            if (!names.add("field " + field.name())) throw duplicate(provider, field.name());
        }

        for (Property property : propertyMembers) {
            if (!names.add("property " + property.name())) throw duplicate(provider, property.name());
        }

        for (Signature method : eventMembers) {
            if (!names.add("method " + method.name())) throw duplicate(provider, method.name());
        }

        for (Signature method : functionMembers) {
            if (!names.add("method " + method.name())) throw duplicate(provider, method.name());
        }

        return new BlueprintLayout(
            provider, List.copyOf(fieldMembers), List.copyOf(propertyMembers),
            List.copyOf(functionMembers), List.copyOf(eventMembers)
        );
    }

    /// `method` as a [Signature], refusing a method a subclass cannot override.
    private static Signature signatureOf(final Method method, final ScriptFunction.BlueprintGraphType graphType) {
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
            throw new IllegalStateException(graphType + " " + describe(method) + " must be neither static nor private");
        }

        return new Signature(
            method,
            Arrays.stream(method.getParameters()).map(Parameter::getName).toList(),
            ParseUtils.camelToSnake(method.getName())
        );
    }

    private static IllegalStateException duplicate(final Class<?> provider, final String name) {
        return new IllegalStateException(
            provider.getName() + " declares more than one blueprint member named " + name);
    }

    private static String describe(final Member member) {
        return member.getDeclaringClass().getName() + "#" + member.getName();
    }

    /// An annotated method.
    ///
    /// @param parameterNames the reflected parameter names, which the engine binds the arguments under
    /// @param key            the method's name in snake case, which a data-driven blueprint stores its graph
    ///                       under
    public record Signature(Method method, List<String> parameterNames, String key) {
        public String name() {
            return method.getName();
        }

        public List<Class<?>> parameterTypes() {
            return List.of(method.getParameterTypes());
        }

        public List<Type> genericParameterTypes() {
            return List.of(method.getGenericParameterTypes());
        }

        public Class<?> returnType() {
            return method.getReturnType();
        }

        public boolean returnsValue() {
            return method.getReturnType() != void.class;
        }

        /// Whether the method has no body of its own to fall back on.
        public boolean isAbstract() {
            return Modifier.isAbstract(method.getModifiers());
        }
    }

    /// A [ScriptFunction.BlueprintGraphType#PROPERTY] getter.
    ///
    /// @param name the getter's name without its `get` / `is` prefix
    /// @param key  `name` in snake case, which a data-driven blueprint stores the value under
    public record Property(Signature getter, String name, String key, ScriptType type) {
    }

    /// A [BlueprintField].
    public record Variable(Field field, ScriptType type) {
        public String name() {
            return field.getName();
        }
    }
}
