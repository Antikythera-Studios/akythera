package com.yesman.akythera.api.component;

import com.yesman.akythera.core.util.LogicalSide;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/// A host that owns a map of [PatchComponent]s keyed by their runtime [Class].
///
/// Implementors only supply the backing map through [#getComponents()]; both lookup methods
/// are provided here. Because a component may itself be a `ComponentAttachable`, hosts nest,
/// and [#getComponent(Class, Class[])] descends that nesting.
@SuppressWarnings("unchecked")
public interface ComponentAttachable {
    /// Joins the names of a [#getComponentAt] path.
    String PATH_SEPARATOR = "/";

    /// Returns all child components
    Map<String, ? extends PatchComponent> getComponents();

    /// The logical side this host lives on.
    LogicalSide logicalSide();

    /// Returns a component by component name, or null while that component is switched off.
    default <C extends PatchComponent> @Nullable C getComponent(final String name) {
        PatchComponent component = getComponents().get(name);

        return component != null && component.isEnabled() ? (C)component : null;
    }

    /// The component at `path`, switched on or off: names joined by [#PATH_SEPARATOR], each one
    /// attached under the one before it. Null when no component sits there.
    default @Nullable PatchComponent getComponentAt(final String path) {
        ComponentAttachable host = this;
        PatchComponent component = null;

        for (String name : path.split(PATH_SEPARATOR)) {
            if (host == null) return null;

            component = host.getComponents().get(name);

            if (component == null) return null;

            host = component instanceof ComponentAttachable attachable ? attachable : null;
        }

        return component;
    }

    /// The [#getComponentAt] path of `component` under this host, or null when it is attached
    /// nowhere under it.
    default @Nullable String pathOf(final PatchComponent component) {
        for (Map.Entry<String, ? extends PatchComponent> entry : getComponents().entrySet()) {
            if (entry.getValue() == component) return entry.getKey();

            if (entry.getValue() instanceof ComponentAttachable host) {
                String below = host.pathOf(component);

                if (below != null) return entry.getKey() + PATH_SEPARATOR + below;
            }
        }

        return null;
    }

    /// Returns a component by component type, or null while that component is switched off. The type
    /// is always [UniqueComponent].
    <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type);

    /// The first switched-on component of `type` this host lists, or null when it lists none.
    ///
    /// The order [#getComponents] keeps is priority order, so this is what a consumer wanting *the*
    /// component of a kind takes when several may exist—an entity's armatures, for instance.
    default <C extends PatchComponent> @Nullable C findComponent(final Class<C> type) {
        for (PatchComponent component : getComponents().values()) {
            if (type.isInstance(component) && component.isEnabled()) return type.cast(component);
        }

        return null;
    }

    /// The first switched-on component of `type` matching `filter` anywhere under this host, depth first in listing
    /// order, or null when none matches. A switched-off host hides what is attached under it.
    default <C extends PatchComponent> @Nullable C findComponentBelow(final Class<C> type, final Predicate<? super C> filter) {
        for (PatchComponent component : getComponents().values()) {
            if (!component.isEnabled()) continue;

            if (type.isInstance(component) && filter.test(type.cast(component))) return type.cast(component);

            if (component instanceof ComponentAttachable host) {
                C below = host.findComponentBelow(type, filter);

                if (below != null) return below;
            }
        }

        return null;
    }

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
