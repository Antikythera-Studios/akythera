package com.yesman.akythera.api.component;

import org.jspecify.annotations.Nullable;

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
}
