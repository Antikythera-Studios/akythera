package com.yesman.akythera.core.blueprint.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// How a widget container is built: an [AkytheraScreen], an [AkytheraWidget] or an [AkytheraHUD] by `usage`,
/// constructed by [#ctor], and the [WidgetContainerScript] it runs. Loaded from data packs and synced on join.
/// Dispatched on `type`.
///
/// The owner a container is built for is the local player's patch for a screen or a HUD, and the parent for a
/// widget.
@ScriptType(autoRegister = false)
public sealed interface WidgetBlueprint extends Blueprint<WidgetContainerScript, ComponentAttachable>
    permits DataDrivenWidgetBlueprint, SourceCodeWidgetBlueprint
{
    Codec<WidgetBlueprint> CODEC = Codec.STRING.dispatch(
        "type",
        blueprint ->
            switch (blueprint) {
                case DataDrivenWidgetBlueprint _ -> "data_driven";
                case SourceCodeWidgetBlueprint _ -> "source_code_script";
            },
        literal ->
            switch (literal) {
                case "data_driven" -> DataDrivenWidgetBlueprint.MAP_CODEC;
                case "source_code_script" -> SourceCodeWidgetBlueprint.MAP_CODEC;
                default -> throw new IllegalArgumentException("Unsupported widget blueprint type " + literal);
            }
    );

    /// Fails the load of every data-driven entry holding a reference cycle, a missing or non-[WidgetUsage#WIDGET]
    /// reference, in a HUD a widget taking input through a reference, a `widget_events` or `widget_bindings` key no
    /// widget answers, or a variable named like an argument its widget events bind.
    RegistryValidator<WidgetBlueprint> VALIDATOR = (registry, loadingErrors) ->
        registry.entrySet().forEach(entry -> {
            if (!(entry.getValue() instanceof DataDrivenWidgetBlueprint blueprint)) return;

            List<String> errors = new ArrayList<>();
            Deque<ResourceKey<WidgetBlueprint>> path = new ArrayDeque<>();
            path.push(entry.getKey());
            validateReferences(registry, blueprint.components(), blueprint.usage() == WidgetUsage.HUD, path, errors);

            Map<String, List<FunctionGraph.Parameter>> events = new HashMap<>();
            Set<String> bindings = new HashSet<>();
            collectSites(blueprint.components(), "", events, bindings);

            Set<String> arguments = new HashSet<>();

            blueprint.widgetEvents().keySet().forEach(key -> {
                if (events.containsKey(key)) {
                    events.get(key).forEach(argument -> arguments.add(argument.name()));
                } else {
                    errors.add("widget_events names " + key + ", which no widget fires");
                }
            });

            blueprint.widgetBindings().keySet().forEach(key -> {
                if (!bindings.contains(key)) errors.add("widget_bindings names " + key + ", which no widget reads");
            });

            blueprint.body().variables().stream().map(ScriptVariables.VariableInfo::name).filter(arguments::contains).forEach(name ->
                errors.add("variable " + name + " shares its name with an argument a widget event binds")
            );

            if (!errors.isEmpty()) loadingErrors.put(entry.getKey(), new IllegalStateException(String.join("; ", errors)));
        });

    WidgetUsage usage();

    /// The [ScriptFunction.Usage#WIDGET_CTOR] function building the container from this blueprint and its owner.
    ScriptFunction ctor();

    /// The screen's title, unused by the other usages.
    LocalizedMessage title();

    @Override
    MapCodec<? extends WidgetBlueprint> mapCodec();

    /// Builds the container `blueprint` describes for `owner` through its [#ctor].
    static ComponentAttachable construct(final Holder.Reference<WidgetBlueprint> blueprint, final @Nullable Object owner) {
        return blueprint.value().ctor().<Holder.Reference<WidgetBlueprint>, Object, ComponentAttachable>castToBiFunction().biFunction().apply(blueprint, owner);
    }

    /// Collects the problems under `components` and every data-driven blueprint they reference into `errors`.
    ///
    /// @param path the blueprints being walked, the innermost first
    private static void validateReferences(
        final Registry<WidgetBlueprint> registry,
        final Map<String, ComponentBlueprint<?, ?>> components,
        final boolean hud,
        final Deque<ResourceKey<WidgetBlueprint>> path,
        final List<String> errors
    ) {
        components.forEach((name, component) -> {
            if (!(component instanceof WidgetComponentBlueprint widget)) return;

            if (widget instanceof WidgetBlueprintComponentBlueprint reference) {
                WidgetBlueprint referenced = registry.getValue(reference.widget());

                if (referenced == null) {
                    errors.add(name + " references the missing widget blueprint " + reference.widget().identifier());
                } else if (referenced.usage() != WidgetUsage.WIDGET) {
                    errors.add(name + " references " + reference.widget().identifier() + ", which is a " + referenced.usage().getSerializedName() + ", not a widget");
                } else if (path.contains(reference.widget())) {
                    errors.add(name + " references " + reference.widget().identifier() + ", which contains itself");
                } else if (referenced instanceof DataDrivenWidgetBlueprint dataDriven) {
                    path.push(reference.widget());
                    validateReferences(registry, dataDriven.components(), hud, path, errors);
                    path.pop();
                }
            } else if (hud && widget.acceptsInput() && widget.children().isEmpty()) {
                errors.add(name + " takes input, which a HUD refuses");
            }

            validateReferences(registry, widget.children(), hud, path, errors);
        });
    }

    /// Collects the `<path>.<event>` keys the widgets under `components` fire, with their argument names, and the
    /// `<path>.<binding>` keys they read.
    ///
    /// @param prefix the path of `components`' host followed by [ComponentAttachable#PATH_SEPARATOR], or empty at
    ///               the root
    private static void collectSites(
        final Map<String, ComponentBlueprint<?, ?>> components,
        final String prefix,
        final Map<String, List<FunctionGraph.Parameter>> events,
        final Set<String> bindings
    ) {
        components.forEach((name, component) -> {
            if (!(component instanceof WidgetComponentBlueprint widget)) return;

            String path = prefix + name;
            widget.events().forEach((event, arguments) -> events.put(path + "." + event, arguments));
            widget.bindings().keySet().forEach(binding -> bindings.add(path + "." + binding));
            collectSites(widget.children(), path + ComponentAttachable.PATH_SEPARATOR, events, bindings);
        });
    }
}
