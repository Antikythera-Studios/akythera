package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/// A widget container run by a hand-written [WidgetContainerScript], a class registered in
/// [AkytheraRegistries.Frozen#SCRIPT_TYPE] with a public constructor taking the container.
///
/// The class is only checked when the container is built: a dedicated server, which lacks the client classes a
/// widget script names, registers it as a placeholder.
///
/// @param script the [ScriptType] of the script class
public record SourceCodeWidgetBlueprint(WidgetUsage usage, ScriptFunction ctor, LocalizedMessage title, ScriptType script) implements WidgetBlueprint {
    public static final MapCodec<SourceCodeWidgetBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                WidgetUsage.CODEC.fieldOf("usage").forGetter(SourceCodeWidgetBlueprint::usage),
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent").forGetter(SourceCodeWidgetBlueprint::ctor),
                LocalizedMessage.CODEC.optionalFieldOf("title", LocalizedMessage.EMPTY).forGetter(SourceCodeWidgetBlueprint::title),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("script").forGetter(SourceCodeWidgetBlueprint::script)
            ).apply(instance, SourceCodeWidgetBlueprint::new)
    );

    /// Builds the script class through its public constructor taking `container`.
    ///
    /// @throws IllegalStateException when the class is no [WidgetContainerScript] with such a constructor
    @Override
    public WidgetContainerScript instantiate(final ComponentAttachable container) {
        if (WidgetContainerScript.class.isAssignableFrom(script.type())) {
            for (Constructor<?> constructor : script.type().getConstructors()) {
                if (constructor.getParameterCount() != 1 || !constructor.getParameterTypes()[0].isInstance(container)) continue;

                try {
                    return (WidgetContainerScript)constructor.newInstance(container);
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException("Failed at binding " + container.getClass().getSimpleName() + " and script " + script.type().getSimpleName(), e);
                }
            }
        }

        throw new IllegalStateException(
            script.type().getSimpleName() + " is not a " + WidgetContainerScript.class.getSimpleName() + " with a public constructor taking "
                + container.getClass().getSimpleName()
        );
    }

    @Override
    public MapCodec<SourceCodeWidgetBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
