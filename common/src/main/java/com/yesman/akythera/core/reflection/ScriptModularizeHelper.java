package com.yesman.akythera.core.reflection;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/// Scans a class and registers its methods as [ScriptFunction] entries, reflectively. Also maps blueprint
/// parents to the classes whose [BlueprintLayout] their editor shows.
public final class ScriptModularizeHelper {
    private ScriptModularizeHelper() {}

    /// The layout provider of each registered parent class, given the parent or subclass being edited.
    private static final Map<Class<?>, UnaryOperator<Class<?>>> BLUEPRINT_PROVIDERS = new ConcurrentHashMap<>();

    /// Every scanned layout, by provider.
    private static final Map<Class<?>, BlueprintLayout> BLUEPRINT_LAYOUTS = new ConcurrentHashMap<>();

    /// Lays out the blueprint editor of `parent` and of every subclass of it with `provider`'s annotated
    /// members.
    public static void registerBlueprintLayout(final Class<?> parent, final Class<?> provider) {
        BLUEPRINT_PROVIDERS.put(parent, _ -> provider);
    }

    /// Lays out the blueprint editor of `parent` and of every subclass of it with the edited class's own
    /// annotated members.
    public static void registerBlueprintLayout(final Class<?> parent) {
        BLUEPRINT_PROVIDERS.put(parent, UnaryOperator.identity());
    }

    /// The class whose layout the blueprint editor of `parent` shows, through the registration of its nearest
    /// registered superclass. Null when none is registered.
    public static @Nullable Class<?> blueprintLayoutProvider(final Class<?> parent) {
        for (Class<?> type = parent; type != null; type = type.getSuperclass()) {
            UnaryOperator<Class<?>> provider = BLUEPRINT_PROVIDERS.get(type);

            if (provider != null) return provider.apply(parent);
        }

        return null;
    }

    /// The layout the blueprint editor of `parent` shows, see [#blueprintLayoutProvider]. Null when no
    /// superclass of `parent` is registered.
    public static @Nullable BlueprintLayout blueprintLayoutOf(final Class<?> parent) {
        Class<?> provider = blueprintLayoutProvider(parent);

        return provider == null ? null : scanBlueprintLayout(provider);
    }

    /// The [BlueprintField] `name` in the layout of `owner`, see [#blueprintLayoutOf].
    ///
    /// @throws IllegalArgumentException when that layout has no such field
    public static BlueprintLayout.Variable blueprintFieldOf(final Class<?> owner, final String name) {
        BlueprintLayout layout = blueprintLayoutOf(owner);
        BlueprintLayout.Variable field = layout == null ? null : layout.field(name);

        if (field == null) throw new IllegalArgumentException(owner.getName() + " has no blueprint field " + name);

        return field;
    }

    /// `provider`'s [BlueprintLayout], scanned once.
    ///
    /// @throws IllegalStateException when a member breaks its annotation's contract
    public static BlueprintLayout scanBlueprintLayout(final Class<?> provider) {
        return BLUEPRINT_LAYOUTS.computeIfAbsent(provider, BlueprintLayout::scan);
    }

    /// Get a registered modularized method by class and method name.
    ///
    /// The lookup matches on the id *path* only (`class_name_method_name`) -- the caller supplies no
    /// namespace, so the first registered entry with that path wins regardless of owning mod.
    ///
    /// @throws IllegalStateException if no function is registered for the given class + method.
    public static ScriptFunction findFunctionByMethod(final Class<?> type, final String name) {
        String path = idPath(type, name);

        for (var entry : AkytheraRegistries.Frozen.SCRIPT_FUNCTION.entrySet()) {
            if (entry.getKey().identifier().getPath().equals(path)) {
                return entry.getValue();
            }
        }

        throw new IllegalStateException(
            "No registered ModularizedFunction for " + type.getSimpleName() + "#" + name + " (expected path '" + path + "')"
        );
    }

    /// Make a custom script function which is expected to be used frequently.
    ///
    /// A [Number] of another type passed to a numeric parameter is converted with explicit-cast semantics
    /// (e.g. `Double` -> `Integer`), the same as a scanned method.
    public static ScriptFunction makeCustomScript(
        final Object sam, final FunctionSignature signature, @Nullable final String label, @Nullable String category,
        @Nullable String desc, final boolean pure, final ScriptFunction.Usage usage
    ) {
        SamArity arity = arityOfSam(sam);
        ScriptFunction.Metadata metadata = new ScriptFunction.Metadata(pure, false, label, category, desc, usage);
        List<Class<?>> parameters = signature.parameters();

        if (parameters.stream().noneMatch(parameter -> Number.class.isAssignableFrom(ParseUtils.box(parameter)))) {
            return wrap(arity, sam, signature, metadata);
        }

        Method samMethod = Arrays.stream(arity.samInterface.getMethods())
            .filter(method -> Modifier.isAbstract(method.getModifiers()))
            .findFirst()
            .orElseThrow();
        MethodHandle impl;

        try {
            impl = MethodHandles.lookup().unreflect(samMethod).bindTo(sam);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed at converting lambda: " + label, e);
        }

        for (int i = 0; i < parameters.size(); i++) {
            Class<?> boxed = ParseUtils.box(parameters.get(i));

            if (Number.class.isAssignableFrom(boxed)) {
                impl = MethodHandles.filterArguments(impl, i, MethodHandles.insertArguments(COERCE_NUMBER, 0, boxed));
            }
        }

        return wrap(arity, synthesize(arity, impl, String.valueOf(label)), signature, metadata);
    }

    /// Scan all annotated fields & methods to be used as [ScriptFunction]. Expose them to
    /// Akythera reflection system to allow building logics in data pack.
    ///
    /// A class marked [com.yesman.akythera.core.reflection.ScriptType] is registered into `types` as well, under its
    /// simple name in snake case.
    public static void scanScriptModulesAndRegister(
        final ModPlatform.Registrar<ScriptFunction> registrar,
        final ModPlatform.Registrar<ScriptType> types,
        final Class<?> cls,
        final @Nullable String category,
        final ScriptFunction.Usage usage
    ) throws Throwable {
        com.yesman.akythera.core.reflection.ScriptType scriptType = cls.getAnnotation(com.yesman.akythera.core.reflection.ScriptType.class);

        if (scriptType != null && scriptType.autoRegister()) {
            types.register(ParseUtils.camelToSnake(cls.getSimpleName()), () -> new ScriptType(cls));
        }

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cls, MethodHandles.lookup());

        for (Field field : cls.getDeclaredFields()) {
            ScriptField scriptField = field.getAnnotation(ScriptField.class);
            if (scriptField == null) continue;

            String base = ParseUtils.camelToSnake(cls.getSimpleName()) + "_";
            String fieldName = ParseUtils.camelToSnake(field.getName());
            String fieldLabel = ParseUtils.camelToSpacedPascal(field.getName());
            // The implicit owner ("self") slot is present on every non-static accessor.
            boolean instanceField = !Modifier.isStatic(field.getModifiers());
            String owner = receiverName(cls);

            // Field accessors are always pure nodes whose (implicit) first parameter is the owner.
            if (scriptField.makeGetter()) {
                // (owner) -> value   [static field: () -> value]
                List<String> parameterNames = instanceField ? List.of(owner) : List.of();
                registerFunction(registrar, base + "get_" + fieldName, category, null, "Get " + fieldLabel, parameterNames, lookup.unreflectGetter(field), usage);
            }

            if (scriptField.makeSetter() && !Modifier.isFinal(field.getModifiers())) {
                // (owner, value) -> value : assign, then yield the value so it composes in the tree.
                List<String> parameterNames = instanceField ? List.of(owner, fieldName) : List.of(fieldName);
                registerFunction(registrar, base + "set_" + fieldName, category, null, "Set " + fieldLabel, parameterNames, returnAssignedValue(lookup.unreflectSetter(field), field.getType()), usage);
            }
        }

        for (Method method : cls.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge() || method.isVarArgs()) { // Vararg methods not supported
                continue;
            }

            if (method.getAnnotation(com.yesman.akythera.core.reflection.ScriptFunction.class) == null) continue;
            com.yesman.akythera.core.reflection.ScriptFunction scriptFuncAnnotation = method.getAnnotation(com.yesman.akythera.core.reflection.ScriptFunction.class);
            MethodHandle impl = lookup.unreflect(method);
            MethodType type = impl.type(); // receiver folded in as param 0 for instance methods
            SamArity arity = arityOf(type.parameterCount(), type.returnType() == void.class);
            if (arity == null) continue;

            // Real runtime types, receiver-first for instance methods -- never Object.
            FunctionSignature signature = FunctionSignature.create(type.returnType(), parameterNames(method, type), type.parameterArray());
            Object sam = synthesize(arity, impl, method.getName());
            String name = idPath(cls, method);
            String label = scriptFuncAnnotation.label().isEmpty()
                ? ParseUtils.camelToSpacedPascal(method.getName())
                : scriptFuncAnnotation.label();
            String methodCategory = StringUtil.isBlank(scriptFuncAnnotation.category())
                ? StringUtil.isBlank(category)
                    ? "Misc"
                    : category
                : scriptFuncAnnotation.category();

            // Static method -> always not a member
            // normal methods -> always member, and determines pure by annotation property
            registerFunction(
                registrar, name, arity, sam, signature,
                new ScriptFunction.Metadata(scriptFuncAnnotation.pure(), !Modifier.isStatic(method.getModifiers()), label, methodCategory, scriptFuncAnnotation.desc(), scriptFuncAnnotation.usage())
            );
        }
    }

    /// [#scanScriptModulesAndRegister] for the client-only class `className` on a physical client. A dedicated
    /// server, which can't load the class, reads its class file instead and registers a [#placeholder] under each
    /// function id the scan registers, and an `Object` type under its type id, so a blueprint naming them still
    /// decodes there.
    public static void scanClientScriptModulesAndRegister(
        final ModPlatform.Registrar<ScriptFunction> registrar,
        final ModPlatform.Registrar<ScriptType> types,
        final String className,
        final @Nullable String category,
        final ScriptFunction.Usage usage
    ) throws Throwable {
        if (ModPlatformProvider.get().getPhysicalSide().isClient()) {
            scanScriptModulesAndRegister(registrar, types, Class.forName(className), category, usage);

            return;
        }

        ClassNode node = new ClassNode();

        try (InputStream stream = ScriptModularizeHelper.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("No class file for " + className);

            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        }

        String typeName = ParseUtils.camelToSnake(className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1));
        String base = typeName + "_";
        AnnotationNode scriptType = annotationOf(node.visibleAnnotations, com.yesman.akythera.core.reflection.ScriptType.class);

        if (scriptType != null && !Boolean.FALSE.equals(valueOf(scriptType, "autoRegister"))) {
            types.register(typeName, () -> new ScriptType(Object.class));
        }

        for (FieldNode field : node.fields) {
            AnnotationNode annotation = annotationOf(field.visibleAnnotations, ScriptField.class);

            if (annotation == null) continue;

            int receiver = (field.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            String fieldName = ParseUtils.camelToSnake(field.name);

            if (!Boolean.FALSE.equals(valueOf(annotation, "makeGetter"))) {
                ScriptFunction getter = placeholder(Collections.nCopies(receiver, "owner"), false, true, base + "get_" + fieldName, usage);
                registrar.register(base + "get_" + fieldName, () -> getter);
            }

            if (!Boolean.FALSE.equals(valueOf(annotation, "makeSetter")) && (field.access & Opcodes.ACC_FINAL) == 0) {
                ScriptFunction setter = placeholder(Collections.nCopies(receiver + 1, "value"), false, true, base + "set_" + fieldName, usage);
                registrar.register(base + "set_" + fieldName, () -> setter);
            }
        }

        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS)) != 0) continue;

            AnnotationNode annotation = annotationOf(method.visibleAnnotations, com.yesman.akythera.core.reflection.ScriptFunction.class);
            int parameterCount = Type.getArgumentCount(method.desc) + ((method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);

            if (annotation == null || arityOf(parameterCount, false) == null) continue;

            String name = base + ParseUtils.camelToSnake(method.name);
            ScriptFunction function = placeholder(
                Collections.nCopies(parameterCount, "parameter"),
                Type.getReturnType(method.desc) == Type.VOID_TYPE,
                Boolean.TRUE.equals(valueOf(annotation, "pure")),
                name,
                valueOf(annotation, "usage") instanceof String[] usageEnum ? ScriptFunction.Usage.valueOf(usageEnum[1]) : ScriptFunction.Usage.SCRIPT
            );
            registrar.register(name, () -> function);
        }
    }

    /// A function taking `parameterNames` and answering null. Stands in on a dedicated server for a function
    /// that names client-only classes, under the same id, so a blueprint naming it still decodes there.
    public static ScriptFunction placeholder(
        final List<String> parameterNames, final boolean returnsVoid, final boolean pure, final String label, final ScriptFunction.Usage usage
    ) {
        SamArity arity = arityOf(parameterNames.size(), returnsVoid);

        if (arity == null) throw new IllegalArgumentException("No function shape takes " + parameterNames.size() + " parameters");

        Class<?>[] parameters = new Class<?>[parameterNames.size()];
        Arrays.fill(parameters, Object.class);

        MethodType type = MethodType.methodType(returnsVoid ? void.class : Object.class, parameters);

        return wrap(
            arity,
            synthesize(arity, MethodHandles.empty(type), label),
            FunctionSignature.create(type.returnType(), parameterNames, parameters),
            new ScriptFunction.Metadata(pure, false, label, null, null, usage)
        );
    }

    /// The annotation of `type` among `annotations`, or null when it is absent.
    private static @Nullable AnnotationNode annotationOf(final @Nullable List<AnnotationNode> annotations, final Class<?> type) {
        if (annotations == null) return null;

        String descriptor = Type.getDescriptor(type);

        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) return annotation;
        }

        return null;
    }

    /// The value `annotation` sets for `name`, or null when it leaves the default.
    private static @Nullable Object valueOf(final AnnotationNode annotation, final String name) {
        if (annotation.values == null) return null;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }

        return null;
    }

    /// Converts the method's handle into a pure functional-interface instance for `arity`.
    /// Numeric parameters are first re-typed to accept any [Number] (explicit-cast
    /// semantics), then the whole handle is adapted to the erased `Object`-typed shape and
    /// wrapped in a hand-rolled SAM calling [MethodHandle#invokeExact].
    ///
    /// Hand-rolled rather than [LambdaMetafactory]: the metafactory only accepts direct,
    /// crackable handles, while both [#coerceNumericParams] and [#returnAssignedValue]
    /// produce adapted combinators.
    private static Object synthesize(SamArity arity, MethodHandle impl, String methodName) {
        final MethodHandle h;

        try {
            h = coerceNumericParams(impl).asType(erasedTypeOf(arity));
        } catch (WrongMethodTypeException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed at converting lambda: " + methodName, e);
        }

        return switch (arity) {
            case SUPPLIER -> (Supplier<Object>) () -> {
                try { return (Object) h.invokeExact(); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case RUNNABLE -> (Runnable) () -> {
                try { h.invokeExact(); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case CONSUMER -> (Consumer<Object>) (p1) -> {
                try { h.invokeExact(p1); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case UNI_FUNCTION -> (Function<Object, Object>) (p1) -> {
                try { return (Object) h.invokeExact(p1); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case BI_FUNCTION -> (BiFunction<Object, Object, Object>) (p1, p2) -> {
                try { return (Object) h.invokeExact(p1, p2); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case TRI_FUNCTION -> (ScriptFunction.TriFunction<Object, Object, Object, Object>) (p1, p2, p3) -> {
                try { return (Object) h.invokeExact(p1, p2, p3); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case QUAD_FUNCTION -> (ScriptFunction.QuadFunction<Object, Object, Object, Object, Object>) (p1, p2, p3, p4) -> {
                try { return (Object) h.invokeExact(p1, p2, p3, p4); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case PENTA_FUNCTION -> (ScriptFunction.QuintFunction<Object, Object, Object, Object, Object, Object>) (p1, p2, p3, p4, p5) -> {
                try { return (Object) h.invokeExact(p1, p2, p3, p4, p5); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case BI_CONSUMER -> (BiConsumer<Object, Object>) (p1, p2) -> {
                try { h.invokeExact(p1, p2); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case TRI_CONSUMER -> (ScriptFunction.TriConsumer<Object, Object, Object>) (p1, p2, p3) -> {
                try { h.invokeExact(p1, p2, p3); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case QUAD_CONSUMER -> (ScriptFunction.QuadConsumer<Object, Object, Object, Object>) (p1, p2, p3, p4) -> {
                try { h.invokeExact(p1, p2, p3, p4); } catch (Throwable t) { throw wrap(t, methodName); }
            };
            case QUINT_CONSUMER -> (ScriptFunction.QuintConsumer<Object, Object, Object, Object, Object>) (p1, p2, p3, p4, p5) -> {
                try { h.invokeExact(p1, p2, p3, p4, p5); } catch (Throwable t) { throw wrap(t, methodName); }
            };
        };
    }

    /// Rethrows unchecked failures as-is and wraps checked ones, specifying the method name.
    private static RuntimeException wrap(Throwable failure, String methodName) {
        return new IllegalStateException("Failed at executing `" + methodName + "`", failure);
    }

    // (Number) -> primitive conversion handles, one per numeric primitive.
    private static final MethodHandle BYTE_VALUE, SHORT_VALUE, INT_VALUE, LONG_VALUE, FLOAT_VALUE, DOUBLE_VALUE;

    /// [#coerceNumber] as a handle.
    private static final MethodHandle COERCE_NUMBER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            BYTE_VALUE   = lookup.findVirtual(Number.class, "byteValue",   MethodType.methodType(byte.class));
            SHORT_VALUE  = lookup.findVirtual(Number.class, "shortValue",  MethodType.methodType(short.class));
            INT_VALUE    = lookup.findVirtual(Number.class, "intValue",    MethodType.methodType(int.class));
            LONG_VALUE   = lookup.findVirtual(Number.class, "longValue",   MethodType.methodType(long.class));
            FLOAT_VALUE  = lookup.findVirtual(Number.class, "floatValue",  MethodType.methodType(float.class));
            DOUBLE_VALUE = lookup.findVirtual(Number.class, "doubleValue", MethodType.methodType(double.class));
            COERCE_NUMBER = MethodHandles.lookup().findStatic(
                ScriptModularizeHelper.class, "coerceNumber", MethodType.methodType(Object.class, Class.class, Object.class)
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Re-types every numeric parameter of `impl` to accept any [Number], converting with
    /// explicit-cast semantics (e.g. `Double` -> `float`) at the boundary. Pairs with the
    /// numeric widening in [FunctionSignature#isParameterCompatible]; non-numeric
    /// parameters are untouched.
    private static MethodHandle coerceNumericParams(MethodHandle impl) {
        MethodType type = impl.type();

        for (int i = 0; i < type.parameterCount(); i++) {
            MethodHandle converter = numberConverter(type.parameterType(i));
            if (converter != null) {
                impl = MethodHandles.filterArguments(impl, i, converter);
            }
        }

        return impl;
    }

    /// `value` as the boxed numeric `type`, converted with explicit-cast semantics when it is another
    /// [Number]. Anything else, null included, is returned as is.
    private static @Nullable Object coerceNumber(final Class<?> type, final @Nullable Object value) {
        if (!(value instanceof Number number) || type.isInstance(value)) return value;
        if (type == Byte.class) return number.byteValue();
        if (type == Short.class) return number.shortValue();
        if (type == Integer.class) return number.intValue();
        if (type == Long.class) return number.longValue();
        if (type == Float.class) return number.floatValue();
        if (type == Double.class) return number.doubleValue();
        return value;
    }

    /// The `(Number) -> param` conversion for a numeric parameter type, or null when the
    /// type is not numeric. `asType` re-boxes when the declared parameter is a wrapper.
    private static @Nullable MethodHandle numberConverter(Class<?> param) {
        MethodHandle conversion;

        if      (param == byte.class   || param == Byte.class)    conversion = BYTE_VALUE;
        else if (param == short.class  || param == Short.class)   conversion = SHORT_VALUE;
        else if (param == int.class    || param == Integer.class) conversion = INT_VALUE;
        else if (param == long.class   || param == Long.class)    conversion = LONG_VALUE;
        else if (param == float.class  || param == Float.class)   conversion = FLOAT_VALUE;
        else if (param == double.class || param == Double.class)  conversion = DOUBLE_VALUE;
        else return null;

        return conversion.asType(MethodType.methodType(param, Number.class));
    }

    /// Synthesizes a field-accessor handle into its SAM and registers it as a pure [ScriptFunction].
    /// Shared by the getter/setter paths -- derives the shape straight from the handle's real types.
    private static void registerFunction(final ModPlatform.Registrar<ScriptFunction> registrar, String name, String category, @Nullable String desc, String label, List<String> parameterNames, MethodHandle handle, ScriptFunction.Usage usage) {
        MethodType type = handle.type();
        SamArity arity = arityOf(type.parameterCount(), type.returnType() == void.class);
        if (arity == null) return;

        FunctionSignature signature = FunctionSignature.create(type.returnType(), parameterNames, type.parameterArray());
        Object sam = synthesize(arity, handle, registrar.namespace() + ":" + name);

        registerFunction(registrar, name, arity, sam, signature, new ScriptFunction.Metadata(true, false, label, category, desc, usage));
    }

    /// Adapts a field-setter handle `(owner?, value)void` into `(owner?, value)value`: it performs the
    /// assignment, then yields the assigned value so the setter has a node shape (every node in the
    /// execution tree must return a value -- a bare 2-arg void has no [SamArity]).
    private static MethodHandle returnAssignedValue(MethodHandle setter, Class<?> fieldType) {
        Class<?>[] params = setter.type().parameterArray();
        Class<?>[] leading = Arrays.copyOf(params, params.length - 1);
        MethodHandle yieldValue = MethodHandles.dropArguments(MethodHandles.identity(fieldType), 0, leading);

        return MethodHandles.foldArguments(yieldValue, setter);
    }

    /// Reflected parameter names for a scanned method, receiver-first so they line up with the
    /// MethodHandle's [MethodType#parameterArray]. Requires compilation with `-parameters` --
    /// otherwise the JDK reports `arg0`, `arg1`, ... For instance methods the receiver is folded
    /// into the handle as param 0 but is absent from [Method#getParameters], so it is named after
    /// the declaring type (e.g. `livingEntityPatch`).
    private static List<String> parameterNames(Method method, MethodType handleType) {
        Parameter[] declared = method.getParameters();
        List<String> names = new ArrayList<>(handleType.parameterCount());

        if (handleType.parameterCount() == declared.length + 1) { // instance method: receiver folded in
            names.add(receiverName(method.getDeclaringClass()));
        }

        for (Parameter parameter : declared) {
            names.add(parameter.getName());
        }

        return names;
    }

    /// The implicit receiver/owner parameter name for `type`: its simple name decapitalized
    /// (e.g. `LivingEntityPatch` -> `livingEntityPatch`).
    private static String receiverName(Class<?> type) {
        String simple = type.getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /// Wraps the synthesized SAM in the matching leaf `ModularizedFunction` record (the prototype).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScriptFunction wrap(SamArity arity, Object sam, FunctionSignature signature, ScriptFunction.Metadata metadata) {
        return switch (arity) {
            case SUPPLIER       -> new ScriptFunction.ScriptSupplier<>((Supplier) sam, signature, metadata);
            case RUNNABLE       -> new ScriptFunction.ScriptRunnable((Runnable) sam, signature, metadata);
            case UNI_FUNCTION   -> new ScriptFunction.ScriptUniFunction<>((Function) sam, signature, metadata);
            case BI_FUNCTION    -> new ScriptFunction.ScriptBiFunction<>((BiFunction) sam, signature, metadata);
            case TRI_FUNCTION   -> new ScriptFunction.ScriptTriFunction<>((ScriptFunction.TriFunction) sam, signature, metadata);
            case QUAD_FUNCTION  -> new ScriptFunction.ScriptQuadFunction<>((ScriptFunction.QuadFunction) sam, signature, metadata);
            case PENTA_FUNCTION -> new ScriptFunction.ScriptQuintFunction<>((ScriptFunction.QuintFunction) sam, signature, metadata);
            case CONSUMER       -> new ScriptFunction.ScriptConsumer<>((Consumer) sam, signature, metadata);
            case BI_CONSUMER    -> new ScriptFunction.ScriptBiConsumer<>((BiConsumer) sam, signature, metadata);
            case TRI_CONSUMER   -> new ScriptFunction.ScriptTriConsumer<>((ScriptFunction.TriConsumer) sam, signature, metadata);
            case QUAD_CONSUMER  -> new ScriptFunction.ScriptQuadConsumer<>((ScriptFunction.QuadConsumer) sam, signature, metadata);
            case QUINT_CONSUMER -> new ScriptFunction.ScriptQuintConsumer<>((ScriptFunction.QuintConsumer) sam, signature, metadata);
        };
    }

    /// Builds the registry entry: a [ModularizedFunctionType] pairing the method's [FunctionSignature]
    /// with the synthesized prototype, then registers it. No per-function codec and no init cycle --
    /// the single recursive [FlowFunctionNode#CODEC] composes prototypes into the execution tree.
    private static void registerFunction(
        final ModPlatform.Registrar<ScriptFunction> registrar, String name, SamArity arity, Object sam, FunctionSignature signature, ScriptFunction.Metadata metadata
    ) {
        ScriptFunction prototype = wrap(arity, sam, signature, metadata);
        registrar.register(name, () -> prototype);
    }

    /// Transform method name into `class_name_method_name`.
    /// e.g. `LivingEntityPatch#getRootMotionComponent` -> `living_entity_patch_get_root_motion_component`.
    /// The registrar prepends the namespace.
    private static String idPath(Class<?> cls, Method method) {
        return idPath(cls, method.getName());
    }

    private static String idPath(Class<?> cls, String methodName) {
        return ParseUtils.camelToSnake(cls.getSimpleName()) + "_" + ParseUtils.camelToSnake(methodName);
    }

    /// The `ModularizedFunction` shape for a folded parameter count + void return, or null if the
    /// method has no shape (> 5 parameters).
    private static SamArity arityOf(int paramCount, boolean returnsVoid) {
        return switch (paramCount) {
            case 0 -> returnsVoid ? SamArity.RUNNABLE : SamArity.SUPPLIER;
            case 1 -> returnsVoid ? SamArity.CONSUMER : SamArity.UNI_FUNCTION;
            case 2 -> returnsVoid ? SamArity.BI_CONSUMER : SamArity.BI_FUNCTION;
            case 3 -> returnsVoid ? SamArity.TRI_CONSUMER : SamArity.TRI_FUNCTION;
            case 4 -> returnsVoid ? SamArity.QUAD_CONSUMER : SamArity.QUAD_FUNCTION;
            case 5 -> returnsVoid ? SamArity.QUINT_CONSUMER : SamArity.PENTA_FUNCTION;
            default -> null;
        };
    }

    /// Detects which [SamArity] shape a raw functional-interface instance implements.
    private static SamArity arityOfSam(Object sam) {
        for (SamArity arity : SamArity.values()) {
            if (arity.samInterface.isInstance(sam)) return arity;
        }

        throw new IllegalArgumentException("Unsupported functional interface for custom script: " + sam.getClass());
    }

    /// The erased `Object`-typed shape a [SamArity]'s functional interface declares.
    private static MethodType erasedTypeOf(SamArity arity) {
        return switch (arity) {
            case SUPPLIER       -> erasedType(0, false);
            case RUNNABLE       -> erasedType(0, true);
            case UNI_FUNCTION   -> erasedType(1, false);
            case BI_FUNCTION    -> erasedType(2, false);
            case TRI_FUNCTION   -> erasedType(3, false);
            case QUAD_FUNCTION  -> erasedType(4, false);
            case PENTA_FUNCTION -> erasedType(5, false);
            case CONSUMER       -> erasedType(1, true);
            case BI_CONSUMER    -> erasedType(2, true);
            case TRI_CONSUMER   -> erasedType(3, true);
            case QUAD_CONSUMER  -> erasedType(4, true);
            case QUINT_CONSUMER -> erasedType(5, true);
        };
    }

    private static MethodType erasedType(int objectParams, boolean returnsVoid) {
        Class<?>[] parameters = new Class<?>[objectParams];
        Arrays.fill(parameters, Object.class);

        return MethodType.methodType(returnsVoid ? void.class : Object.class, parameters);
    }

    /// The `ModularizedFunction` shapes, picked by folded parameter count + void return.
    public enum SamArity {
        SUPPLIER(Supplier.class),
        RUNNABLE(Runnable.class),
        CONSUMER(Consumer.class),
        UNI_FUNCTION(Function.class),
        BI_FUNCTION(BiFunction.class),
        TRI_FUNCTION(ScriptFunction.TriFunction.class),
        QUAD_FUNCTION(ScriptFunction.QuadFunction.class),
        PENTA_FUNCTION(ScriptFunction.QuintFunction.class),
        BI_CONSUMER(BiConsumer.class),
        TRI_CONSUMER(ScriptFunction.TriConsumer.class),
        QUAD_CONSUMER(ScriptFunction.QuadConsumer.class),
        QUINT_CONSUMER(ScriptFunction.QuintConsumer.class);

        /// The functional interface of this shape.
        private final Class<?> samInterface;

        SamArity(final Class<?> samInterface) {
            this.samInterface = samInterface;
        }
    }

}