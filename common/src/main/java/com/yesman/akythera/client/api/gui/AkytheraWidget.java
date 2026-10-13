package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.script.HUDScript;
import com.yesman.akythera.client.api.gui.script.WidgetScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/// A container of widgets attached by name, placed in an [AkytheraScreen] or another [AkytheraWidget].
/// Every attached [AnchoredWidget] is anchored against this widget's rect, in attachment order. It takes input
/// only as its screen hands it down.
///
/// One built from a [WidgetBlueprint] runs its script: a [WidgetScript], which answers the input the widgets leave
/// unhandled, or for an [AkytheraHUD] a [HUDScript].
@ScriptType
public class AkytheraWidget extends AbstractContainerWidget implements AnchoredWidget, TickableComponent, ComponentAttachable {
    /// The attached widgets by name, in attachment order.
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    /// The attached widgets in attachment order, as [#children] lists them.
    private final List<AbstractWidget> widgets = new ArrayList<>();

    /// The rect each child was last laid out in.
    private final Map<AbstractWidget, ScreenRectangle> childAreas = new WeakHashMap<>();

    /// The blueprint the widget was built from, or null for one built in code.
    private final Holder.@Nullable Reference<WidgetBlueprint> blueprint;

    /// What the widget was built for: the local player's patch for a HUD, the parent for a nested widget.
    private final @Nullable Object owner;

    private final @Nullable WidgetContainerScript script;

    public AkytheraWidget(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.blueprint = null;
        this.owner = null;
        this.script = null;
    }

    /// Builds the widget `blueprint` describes for `owner`, filling its parent: attaches the widgets its script
    /// lists, then constructs a [WidgetScript].
    public AkytheraWidget(final Holder.Reference<WidgetBlueprint> blueprint, final @Nullable Object owner) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.x1 = AnchoredWidget.pixel(0);
        this.x2 = AnchoredWidget.pixel(0);
        this.y1 = AnchoredWidget.pixel(0);
        this.y2 = AnchoredWidget.pixel(0);
        this.horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        this.verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;
        this.blueprint = blueprint;
        this.owner = owner;
        this.script = blueprint.value().instantiate(this);

        BlueprintWidgets.build(script, this);

        if (script instanceof WidgetScript widgetScript) widgetScript.construct();
    }

    public Holder.@Nullable Reference<WidgetBlueprint> getBlueprint() {
        return blueprint;
    }

    public @Nullable Object getOwner() {
        return owner;
    }

    public @Nullable WidgetContainerScript getScript() {
        return script;
    }

    /// Attaches `widget` under `name` and lays the children out again. A widget already attached under `name`
    /// is replaced in its place.
    public <W extends AbstractWidget & PatchComponent> void addComponent(final String name, final W widget) {
        PatchComponent previous = components.put(name, widget);

        if (previous instanceof AbstractWidget replaced) {
            widgets.set(widgets.indexOf(replaced), widget);

            if (getFocused() == replaced) setFocused(null);
        } else {
            widgets.add(widget);
        }

        relocateChildren();
    }

    /// Detaches the widget attached under `name` and lays the rest out again. Returns it, or null when nothing
    /// is attached there.
    public @Nullable PatchComponent removeComponent(final String name) {
        PatchComponent removed = components.remove(name);

        if (removed instanceof AbstractWidget widget) {
            widgets.remove(widget);

            if (getFocused() == widget) setFocused(null);

            relocateChildren();
        }

        return removed;
    }

    /// Detaches every widget.
    public void clearComponents() {
        components.clear();
        widgets.clear();
        setFocused(null);
    }

    /// Reorders the attached widgets by `comparator` and lays them out again.
    public void sortComponents(final Comparator<? super AbstractWidget> comparator) {
        List<Map.Entry<String, PatchComponent>> sorted = components.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
            .sorted((first, second) -> comparator.compare((AbstractWidget) first.getValue(), (AbstractWidget) second.getValue()))
            .toList();

        components.clear();
        widgets.clear();

        for (Map.Entry<String, PatchComponent> entry : sorted) {
            components.put(entry.getKey(), entry.getValue());
            widgets.add((AbstractWidget) entry.getValue());
        }

        relocateChildren();
    }

    /// Ticks the enabled [TickableComponent]s attached.
    @Override
    public void tick() {
        for (PatchComponent component : components.values()) {
            if (component.isEnabled() && component instanceof TickableComponent tickable) tickable.tick();
        }

        if (script instanceof WidgetScript widgetScript) widgetScript.tick();
    }

    /// Lays the widget out, telling a [WidgetScript] when its size changed.
    @Override
    public void relocate(final ScreenRectangle screenRectangle) {
        int width = getWidth();
        int height = getHeight();

        AnchoredWidget.super.relocate(screenRectangle);

        if (script instanceof WidgetScript widgetScript && (width != getWidth() || height != getHeight())) {
            widgetScript.onResized(getWidth(), getHeight());
        }
    }

    /// Lays every attached [AnchoredWidget] out in this widget's content rect.
    @Override
    public void relocateChildren() {
        ScreenRectangle area = getContentRectangle();

        for (AbstractWidget widget : widgets) {
            if (widget instanceof AnchoredWidget) placeChild(widget, area);
        }
    }

    /// Lays `child` out in `area`: an [AnchoredWidget] anchors in it, any other widget fills it.
    protected void placeChild(final AbstractWidget child, final ScreenRectangle area) {
        childAreas.put(child, area);

        if (child instanceof AnchoredWidget anchored) {
            anchored.relocate(area);
        } else {
            child.setRectangle(area.width(), area.height(), area.left(), area.top());
        }
    }

    /// The rect `child` was last laid out in, or null before its first layout.
    public @Nullable ScreenRectangle childArea(final AbstractWidget child) {
        return childAreas.get(child);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        for (AbstractWidget widget : widgets) {
            widget.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    // ***************************************************************
    // [WidgetScript] input
    // ***************************************************************
    /// Hands the pointer to every widget, then to the script.
    @Override
    public void mouseMoved(final double mouseX, final double mouseY) {
        for (AbstractWidget widget : widgets) {
            widget.mouseMoved(mouseX, mouseY);
        }

        if (script instanceof WidgetScript widgetScript) widgetScript.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || script instanceof WidgetScript widgetScript && widgetScript.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
        return super.mouseReleased(event) || script instanceof WidgetScript widgetScript && widgetScript.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dragX, final double dragY) {
        return super.mouseDragged(event, dragX, dragY) || script instanceof WidgetScript widgetScript && widgetScript.mouseDragged(event, dragX, dragY);
    }

    /// Hands the wheel to the widget under the pointer, then scrolls this container when its content overflows, then
    /// to the script.
    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent()
            || scrollable() && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || script instanceof WidgetScript widgetScript && widgetScript.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        return super.keyPressed(event) || script instanceof WidgetScript widgetScript && widgetScript.keyPressed(event);
    }

    @Override
    public boolean keyReleased(final @NonNull KeyEvent event) {
        return super.keyReleased(event) || script instanceof WidgetScript widgetScript && widgetScript.keyReleased(event);
    }

    @Override
    public boolean charTyped(final @NonNull CharacterEvent event) {
        return super.charTyped(event) || script instanceof WidgetScript widgetScript && widgetScript.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(final @NonNull PreeditEvent event) {
        return super.preeditUpdated(event) || script instanceof WidgetScript widgetScript && widgetScript.preeditUpdated(event);
    }

    @Override
    public @NonNull List<AbstractWidget> children() {
        return widgets;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [ComponentAttachable] implementations
    // ***************************************************************
    @Override
    public Map<String, PatchComponent> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT;
    }

    @Override
    public <C extends UniqueComponent> @Nullable C getComponent(final Class<C> type) {
        return findComponent(type);
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
