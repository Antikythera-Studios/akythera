package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.client.api.gui.script.DataDrivenHUDScript;
import com.yesman.akythera.client.api.gui.script.DataDrivenScreenScript;
import com.yesman.akythera.client.api.gui.script.DataDrivenWidgetScript;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.blueprint.widget.ButtonBlueprint;
import com.yesman.akythera.core.blueprint.widget.CheckBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.ColorPickerBlueprint;
import com.yesman.akythera.core.blueprint.widget.ComboBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.DragValueBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.EditBoxBlueprint;
import com.yesman.akythera.core.blueprint.widget.GridBlueprint;
import com.yesman.akythera.core.blueprint.widget.ImageBlueprint;
import com.yesman.akythera.core.blueprint.widget.LinearContainerBlueprint;
import com.yesman.akythera.core.blueprint.widget.ProgressionBarBlueprint;
import com.yesman.akythera.core.blueprint.widget.TextAreaBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprintComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Builds the containers [WidgetBlueprint]s describe and the widgets they lay out. Each widget holds its own value,
/// and runs its events and bindings in the data-driven script of the container being built around it.
public final class BlueprintWidgets {
    private BlueprintWidgets() {}

    /// The containers being built, the innermost first.
    private static final Deque<Frame> BUILDING = new ArrayDeque<>();

    /// Builds the screen or the HUD `blueprint` describes for the local player's patch, and shows it: a screen
    /// replaces the open one, a HUD goes over the shown ones. Null for a [WidgetUsage#WIDGET] blueprint, which only
    /// shows inside another, or with no local player.
    public static @Nullable ComponentAttachable open(final Holder.Reference<WidgetBlueprint> blueprint) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || blueprint.value().usage() == WidgetUsage.WIDGET) return null;

        ComponentAttachable container = WidgetBlueprint.construct(blueprint, AkytheraPatches.getEntityPatch(player));

        switch (container) {
            case AkytheraScreen screen -> Minecraft.getInstance().gui.setScreen(screen);
            case AkytheraHUD hud -> HudManager.getInstance().addHud(hud);
            default -> Akythera.LOGGER.warn("Widget blueprint {} built a {}, neither a screen nor a HUD", blueprint.key().identifier(), container.getClass().getSimpleName());
        }

        return container;
    }

    /// Attaches the widgets `script` lists to `container`, by [WidgetComponentBlueprint#order], with their events
    /// and bindings answered by `script`.
    public static void build(final WidgetContainerScript script, final ComponentAttachable container) {
        BUILDING.push(new Frame(script, new ArrayDeque<>()));

        try {
            attach(script.componentsToAttach(), container);
        } finally {
            BUILDING.pop();
        }
    }

    /// The data-driven script of `container`'s kind.
    public static WidgetContainerScript dataDrivenScript(final Holder.Reference<DataDrivenWidgetBlueprint> blueprint, final ComponentAttachable container) {
        return switch (container) {
            case AkytheraScreen screen -> new DataDrivenScreenScript(blueprint, screen);
            case AkytheraHUD hud -> new DataDrivenHUDScript(blueprint, hud);
            case AkytheraWidget widget -> new DataDrivenWidgetScript(blueprint, widget);
            default -> throw new IllegalArgumentException("A widget blueprint can't build a " + container.getClass().getSimpleName());
        };
    }

    /// Builds the referenced [WidgetUsage#WIDGET] blueprint for `host`, placed by the reference's layout.
    public static AkytheraWidget reference(final WidgetBlueprintComponentBlueprint blueprint, final ComponentAttachable host) {
        Holder.Reference<WidgetBlueprint> referenced = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.WIDGET_KEY).getOrThrow(blueprint.widget());
        AkytheraWidget widget = (AkytheraWidget)WidgetBlueprint.construct(referenced, host);
        WidgetLayout layout = blueprint.layout();
        widget.setX1(unit(layout.x1()));
        widget.setX2(unit(layout.x2()));
        widget.setY1(unit(layout.y1()));
        widget.setY2(unit(layout.y2()));
        widget.setHorizontalAnchorType(horizontal(layout));
        widget.setVerticalAnchorType(vertical(layout));
        widget.setEnabled(blueprint.enabled());

        return widget;
    }

    /// An empty widget placed by `layout`.
    public static AkytheraWidget widget(final WidgetLayout layout) {
        return new AkytheraWidget(unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()), horizontal(layout), vertical(layout));
    }

    public static AkytheraButton button(final ButtonBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Consumer<Object[]> pressed = site().event(blueprint, WidgetComponentBlueprint.ON_PRESSED);
        AkytheraButton button = AkytheraButton.buttonBuilder(font(), blueprint.message(), pressed == null ? () -> {} : () -> pressed.accept(new Object[0]))
            .xUnits(unit(layout.x1()), unit(layout.x2()))
            .yUnits(unit(layout.y1()), unit(layout.y2()))
            .horizontalAnchorType(horizontal(layout))
            .verticalAnchorType(vertical(layout))
            .theme(theme(blueprint.theme(), ButtonTheme.class))
            .images(blueprint.normalImage(), blueprint.focusedImage(), blueprint.disabledImage())
            .hoverSound(blueprint.hoverSound())
            .pressSound(blueprint.pressSound())
            .build();
        button.setEnabled(blueprint.enabled());

        return button;
    }

    public static CheckBox checkBox(final CheckBoxBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        MutableObject<Boolean> value = new MutableObject<>(false);
        CheckBox checkBox = new CheckBox(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site().event(blueprint, WidgetComponentBlueprint.ON_CHANGED)),
            blueprint.message().toComponent(),
            true
        );
        CheckBoxTheme theme = theme(blueprint.theme(), CheckBoxTheme.class);

        if (theme != null) checkBox.setTheme(theme);

        checkBox.setEnabled(blueprint.enabled());

        return checkBox;
    }

    public static ComboBox<LocalizedMessage> comboBox(final ComboBoxBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        MutableObject<@Nullable LocalizedMessage> value = new MutableObject<>();
        ComboBox<LocalizedMessage> comboBox = new ComboBox<>(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site().event(blueprint, WidgetComponentBlueprint.ON_CHANGED)),
            blueprint.maxRows(),
            blueprint.title().toComponent(),
            blueprint.items(),
            item -> item.toComponent().getString()
        )
            .setSearchable(blueprint.searchable())
            .setClearable(blueprint.clearable());
        ComboBoxTheme theme = theme(blueprint.theme(), ComboBoxTheme.class);

        if (theme != null) comboBox.setTheme(theme);

        comboBox.setEnabled(blueprint.enabled());

        return comboBox;
    }

    public static AkytheraEditBox<String> editBox(final EditBoxBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Site site = site();
        MutableObject<String> value = new MutableObject<>("");
        AkytheraEditBox<String> editBox = AkytheraEditBox.stringBox(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site.event(blueprint, WidgetComponentBlueprint.ON_CHANGED)),
            blueprint.placeholder().toComponent()
        );
        Consumer<Object[]> committed = site.event(blueprint, WidgetComponentBlueprint.ON_COMMITTED);
        EditBoxTheme theme = theme(blueprint.theme(), EditBoxTheme.class);

        if (committed != null) editBox.setOnCommitted((previous, current) -> committed.accept(new Object[] {previous, current}));
        if (theme != null) editBox.setTheme(theme);

        editBox.setEnabled(blueprint.enabled());

        return editBox;
    }

    public static DragValueBox<Float> dragValueBox(final DragValueBoxBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Site site = site();
        MutableObject<Float> value = new MutableObject<>(blueprint.min());
        DragValueBox<Float> dragValueBox = new DragValueBox<>(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site.event(blueprint, WidgetComponentBlueprint.ON_CHANGED)),
            blueprint.min(), blueprint.max(),
            Float::parseFloat, ParseUtils::floatingPointToString, d -> (float)d,
            blueprint.message().toComponent()
        );
        Consumer<Object[]> committed = site.event(blueprint, WidgetComponentBlueprint.ON_COMMITTED);
        DragValueBoxTheme theme = theme(blueprint.theme(), DragValueBoxTheme.class);

        if (committed != null) dragValueBox.setOnCommitted((previous, current) -> committed.accept(new Object[] {previous, current}));
        if (theme != null) dragValueBox.setTheme(theme);

        dragValueBox.setEnabled(blueprint.enabled());

        return dragValueBox;
    }

    public static TextArea textArea(final TextAreaBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Site site = site();
        MutableObject<String> value = new MutableObject<>("");
        TextArea textArea = new TextArea(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site.event(blueprint, WidgetComponentBlueprint.ON_CHANGED)),
            LocalizedMessage.EMPTY.toComponent()
        ).setEditable(blueprint.editable());
        Consumer<Object[]> committed = site.event(blueprint, WidgetComponentBlueprint.ON_COMMITTED);

        if (committed != null) textArea.setOnCommitted((previous, current) -> committed.accept(new Object[] {previous, current}));

        textArea.setText(blueprint.text());
        textArea.setEnabled(blueprint.enabled());

        return textArea;
    }

    public static Image image(final ImageBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Image image = new Image(
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            blueprint.image()
        );
        image.setEnabled(blueprint.enabled());

        return image;
    }

    /// A bar whose `progression` binding is polled every tick, or one only scripts move when the blueprint binds
    /// none.
    public static ProgressionBar progressionBar(final ProgressionBarBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Supplier<@Nullable Object> binding = site().binding(ProgressionBarBlueprint.PROGRESSION);
        ProgressionBar progressionBar = new ProgressionBar(
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            binding == null ? null : () -> binding.get() instanceof Number number ? number.floatValue() : 0.0F
        )
            .setFillStyle(blueprint.fillStyle())
            .setFillDirection(blueprint.fillDirection())
            .setHighlightThickness(blueprint.highlightThickness())
            .setFlashTime(blueprint.flashTime())
            .setLerpTime(blueprint.lerpTime(), blueprint.fixedLerpTime())
            .setImages(blueprint.filledImage(), blueprint.emptyImage(), blueprint.lerpImage());
        progressionBar.setEnabled(blueprint.enabled());

        return progressionBar;
    }

    public static ColorPickerWidget colorPicker(final ColorPickerBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        MutableObject<RGBA> value = new MutableObject<>(blueprint.color());
        ColorPickerWidget colorPicker = new ColorPickerWidget(
            font(),
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            value::getValue, changed(value::setValue, site().event(blueprint, WidgetComponentBlueprint.ON_CHANGED))
        );
        colorPicker.setEnabled(blueprint.enabled());

        return colorPicker;
    }

    public static Grid grid(final GridBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        Grid grid = new Grid(
            unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()),
            horizontal(layout), vertical(layout),
            blueprint.cellWidth(), blueprint.cellHeight(), blueprint.padding(),
            null
        );
        grid.setEnabled(blueprint.enabled());
        attach(blueprint.children(), grid);

        return grid;
    }

    public static LinearWidgetContainer linearContainer(final LinearContainerBlueprint blueprint) {
        WidgetLayout layout = blueprint.layout();
        LinearWidgetContainer container = blueprint.vertical()
            ? new VerticalWidgetContainer(unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()), horizontal(layout), vertical(layout), blueprint.padding())
            : new HorizontalWidgetContainer(unit(layout.x1()), unit(layout.x2()), unit(layout.y1()), unit(layout.y2()), horizontal(layout), vertical(layout), blueprint.padding());
        container.setEnabled(blueprint.enabled());
        attach(blueprint.children(), container);

        return container;
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// A container being built.
    ///
    /// @param script what answers its widgets' events and bindings
    /// @param path   the names down to the widget being built
    private record Frame(WidgetContainerScript script, Deque<String> path) {
    }

    /// The widget being built, at `path` in the container whose script is `script`, or outside any for null.
    private record Site(@Nullable WidgetContainerScript script, String path) {
        /// What runs when `widget`'s `event` fires, or null when nothing does.
        @Nullable Consumer<Object[]> event(final WidgetComponentBlueprint widget, final String event) {
            return script == null
                ? null
                : script.widgetEvent(path + "." + event, widget.events().get(event).stream().map(FunctionGraph.Parameter::name).toList());
        }

        /// What answers the widget's `binding`, or null when nothing does.
        @Nullable Supplier<@Nullable Object> binding(final String binding) {
            return script == null ? null : script.widgetBinding(path + "." + binding);
        }
    }

    /// The widget being built. One built outside [#build] runs no graph.
    private static Site site() {
        Frame frame = BUILDING.peek();

        return frame == null ? new Site(null, "") : new Site(frame.script(), String.join(ComponentAttachable.PATH_SEPARATOR, frame.path()));
    }

    /// `setter`, followed by `event` with the value as its argument when the blueprint has a graph for it.
    private static <T> Consumer<T> changed(final Consumer<T> setter, final @Nullable Consumer<Object[]> event) {
        return event == null ? setter : value -> {
            setter.accept(value);
            event.accept(new Object[] {value});
        };
    }

    /// Builds every widget of `components` and attaches it to `host`, an [AkytheraScreen] or an [AkytheraWidget], by
    /// [WidgetComponentBlueprint#order].
    @SuppressWarnings("unchecked")
    private static <W extends AbstractWidget & PatchComponent> void attach(final Map<String, ComponentBlueprint<?, ?>> components, final ComponentAttachable host) {
        Deque<String> path = BUILDING.isEmpty() ? new ArrayDeque<>() : BUILDING.peek().path();

        components.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<String, ComponentBlueprint<?, ?>>>comparingInt(entry -> entry.getValue() instanceof WidgetComponentBlueprint widget ? widget.order() : 0)
                    .thenComparing(Map.Entry::getKey)
            )
            .filter(entry -> entry.getValue().buildsFor(true))
            .forEach(entry -> {
                path.addLast(entry.getKey());

                W widget;

                try {
                    widget = (W)((ComponentBlueprint<?, Object>)entry.getValue()).instantiate(host);
                } finally {
                    path.removeLast();
                }

                switch (host) {
                    case AkytheraScreen screen -> screen.addComponent(entry.getKey(), widget);
                    case AkytheraWidget container -> container.addComponent(entry.getKey(), widget);
                    default -> throw new IllegalArgumentException("Can't attach widgets to " + host.getClass().getSimpleName());
                }
            });
    }

    /// The theme named `name` when it is a `type`. Null for no name, or one naming no such theme.
    private static <T extends WidgetTheme> @Nullable T theme(final @Nullable Identifier name, final Class<T> type) {
        if (name == null) return null;

        T theme = WidgetTheme.ENUM_MANAGER.get(name).filter(type::isInstance).map(type::cast).orElse(null);

        if (theme == null) Akythera.LOGGER.warn("No {} named {}", type.getSimpleName(), name);

        return theme;
    }

    /// `widget`'s units and anchor types as layout data, each unit keeping its pixel or percentage kind.
    public static WidgetLayout layoutOf(final AnchoredWidget widget) {
        return new WidgetLayout(
            layoutUnit(widget.getX1()), layoutUnit(widget.getX2()), layoutUnit(widget.getY1()), layoutUnit(widget.getY2()),
            WidgetLayout.Horizontal.valueOf(widget.getHorizontalAnchorType().name()),
            WidgetLayout.Vertical.valueOf(widget.getVerticalAnchorType().name())
        );
    }

    private static WidgetLayout.Unit layoutUnit(final AnchoredWidget.Unit unit) {
        return unit instanceof AnchoredWidget.Fraction(int value)
            ? new WidgetLayout.Unit(value, true)
            : new WidgetLayout.Unit(unit.pixels(ScreenRectangle.empty(), true), false);
    }

    private static AnchoredWidget.Unit unit(final WidgetLayout.Unit unit) {
        return unit.fraction() ? AnchoredWidget.fraction(unit.value()) : AnchoredWidget.pixel(unit.value());
    }

    private static AnchoredWidget.HorizontalAnchorType horizontal(final WidgetLayout layout) {
        return AnchoredWidget.HorizontalAnchorType.valueOf(layout.horizontalAnchor().name());
    }

    private static AnchoredWidget.VerticalAnchorType vertical(final WidgetLayout layout) {
        return AnchoredWidget.VerticalAnchorType.valueOf(layout.verticalAnchor().name());
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
