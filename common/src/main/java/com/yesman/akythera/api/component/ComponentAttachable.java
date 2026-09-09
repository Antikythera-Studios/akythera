package com.yesman.akythera.api.component;

import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// A host that owns a map of [PatchComponent]s keyed by their runtime [Class].
///
/// Implementors only supply the backing map through [#getComponents()]; both lookup methods
/// are provided here. Because a component may itself be a `ComponentAttachable`, hosts nest,
/// and [#getComponent(Class, Class[])] descends that nesting.
@SuppressWarnings("unchecked")
public interface ComponentAttachable {
    /// Returns all child components
    Map<String, ? extends PatchComponent> getComponents();

    /// Returns a component by component name
    default <C extends PatchComponent> @Nullable C getComponent(final String name) {
        return (C)getComponents().get(name);
    }

    /// Returns a component by component type. The type is always [UniqueComponent].
    <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type);

    /// Indexes the [UniqueComponent]s among `components` by their runtime class — the map
    /// [#getComponent(Class)] answers from.
    ///
    /// @throws IllegalStateException when two components share a [UniqueComponent] type
    static Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> indexUnique(
        final Map<String, ? extends PatchComponent> components
    ) {
        Map<Class<? extends UniqueComponent>, UniqueComponent> index = new HashMap<>();

        for (Map.Entry<String, ? extends PatchComponent> entry : components.entrySet()) {
            if (!(entry.getValue() instanceof UniqueComponent unique)) continue;

            if (index.containsKey(unique.getClass())) {
                throw new IllegalStateException(
                    MessageFormat.format("""
                        Detected more than one component of {0}, {0} is UniqueComponent that does not allow duplicated types of components. {1} must be removed to properly launch the game.
                    """, unique.getClass(), entry.getKey())
                );
            }

            index.put(unique.getClass(), unique);
        }

        return Collections.unmodifiableMap(index);
    }
}
