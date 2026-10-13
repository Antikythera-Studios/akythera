package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Map;

/// A widget a [WidgetBlueprint] lays out.
///
/// Only the client builds one: a dedicated server reads and syncs the data, and [#instantiate] hands over to the
/// client's widget classes, which no codec or record here touches.
public interface WidgetComponentBlueprint extends ComponentBlueprint<PatchComponent, ComponentAttachable> {
    /// The field every widget's codec declares for [#layout].
    MapCodec<WidgetLayout> LAYOUT_CODEC = WidgetLayout.CODEC.fieldOf("layout");

    /// The field every widget's codec declares for [#order].
    MapCodec<Integer> ORDER_CODEC = Codec.INT.optionalFieldOf("order", 0);

    /// The event a pressed widget fires, binding nothing.
    String ON_PRESSED = "on_pressed";

    /// The event a widget fires when its value changes, binding the new value.
    String ON_CHANGED = "on_changed";

    /// The event an edited widget fires when the edit is committed, binding the old and the new value.
    String ON_COMMITTED = "on_committed";

    /// A container's children, dispatched on the widget component type id.
    Codec<Map<String, ComponentBlueprint<?, ?>>> CHILDREN_CODEC = ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.WIDGET_COMPONENT_TYPES);

    WidgetLayout layout();

    /// Where the widget sits among its siblings, ascending. Ties fall back to the name.
    int order();

    /// Whether the widget takes input. A [WidgetUsage#HUD] blueprint holds only widgets that take none.
    default boolean acceptsInput() {
        return true;
    }

    /// The events this widget fires into its blueprint's [DataDrivenWidgetBlueprint#widgetEvents], by name, with
    /// the arguments they bind as locals.
    default Map<String, List<FunctionGraph.Parameter>> events() {
        return Map.of();
    }

    /// The values this widget reads from its blueprint's [DataDrivenWidgetBlueprint#widgetBindings] every tick, by
    /// name, with the type each graph answers.
    default Map<String, ScriptType> bindings() {
        return Map.of();
    }

    /// The widgets attached under this one, by name. Empty for a widget that holds none.
    default Map<String, ComponentBlueprint<?, ?>> children() {
        return Map.of();
    }

    @Override
    default boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    /// The arguments [#ON_CHANGED] binds: the new value, of `type`.
    static List<FunctionGraph.Parameter> changedArguments(final Holder<ScriptType> type) {
        return List.of(new FunctionGraph.Parameter("value", type.value()));
    }

    /// The arguments [#ON_COMMITTED] binds: the old and the new value, of `type`.
    static List<FunctionGraph.Parameter> committedArguments(final Holder<ScriptType> type) {
        return List.of(new FunctionGraph.Parameter("previous", type.value()), new FunctionGraph.Parameter("value", type.value()));
    }

    /// Whether any of `children` takes input.
    static boolean anyAcceptsInput(final Map<String, ComponentBlueprint<?, ?>> children) {
        return children.values().stream().anyMatch(child -> child instanceof WidgetComponentBlueprint widget && widget.acceptsInput());
    }
}
