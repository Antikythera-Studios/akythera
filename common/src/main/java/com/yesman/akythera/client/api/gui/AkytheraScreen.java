package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.script.ScreenScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// A screen of widgets attached by name, opened through [Gui#setScreen]. Every attached widget is
/// anchored against the screen. A resize lays the widgets out again instead of rebuilding them, and the
/// [ModalManager] layer draws and takes input over them.
///
/// One built from a [WidgetBlueprint] runs its [ScreenScript], which answers the input the widgets leave unhandled.
@ScriptType
public class AkytheraScreen extends Screen implements ModalAccessibleScreen, ComponentAttachable {
    /// The attached widgets by name, in attachment order.
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    /// The blueprint the screen was built from, or null for one built in code.
    private final Holder.@Nullable Reference<WidgetBlueprint> blueprint;

    /// What the screen was built for, the local player's patch for a blueprint screen.
    private final @Nullable Object owner;

    private final @Nullable ScreenScript script;

    public AkytheraScreen(final Component title) {
        super(title);

        this.blueprint = null;
        this.owner = null;
        this.script = null;
    }

    /// Builds the screen `blueprint` describes for `owner`: attaches the widgets its script lists, then constructs
    /// the script.
    public AkytheraScreen(final Holder.Reference<WidgetBlueprint> blueprint, final @Nullable Object owner) {
        super(blueprint.value().title().toComponent());

        this.blueprint = blueprint;
        this.owner = owner;
        this.script = (ScreenScript)blueprint.value().instantiate(this);

        BlueprintWidgets.build(script, this);
        script.construct();
    }

    public Holder.@Nullable Reference<WidgetBlueprint> getBlueprint() {
        return blueprint;
    }

    public @Nullable Object getOwner() {
        return owner;
    }

    public @Nullable ScreenScript getScript() {
        return script;
    }

    /// Attaches `widget` under `name` and, when it is an [AnchoredWidget], lays it out against the screen. A
    /// widget already attached under `name` is replaced.
    public <W extends AbstractWidget & PatchComponent> void addComponent(final String name, final W widget) {
        PatchComponent previous = components.put(name, widget);

        if (previous instanceof GuiEventListener replaced) removeWidget(replaced);

        addRenderableWidget(widget);

        if (widget instanceof AnchoredWidget anchored) anchored.relocate(getRectangle());
    }

    /// Detaches the widget attached under `name`. Returns it, or null when nothing is attached there.
    public @Nullable PatchComponent removeComponent(final String name) {
        PatchComponent removed = components.remove(name);

        if (removed instanceof GuiEventListener widget) removeWidget(widget);

        return removed;
    }

    /// Lays out the widgets attached before the screen opened.
    @Override
    protected void init() {
        relocateComponents();
    }

    /// Lays the widgets and the modals out against the new size, keeping them.
    @Override
    protected void repositionElements() {
        relocateComponents();
        ModalManager.getInstance().relocateModals();

        if (script != null) script.onResized(width, height);
    }

    private void relocateComponents() {
        ScreenRectangle rectangle = getRectangle();

        for (PatchComponent component : components.values()) {
            if (component instanceof AnchoredWidget anchored) anchored.relocate(rectangle);
        }
    }

    /// Ticks the enabled [TickableComponent]s attached.
    @Override
    public void tick() {
        for (PatchComponent component : components.values()) {
            if (component.isEnabled() && component instanceof TickableComponent tickable) tickable.tick();
        }

        if (script != null) script.tick();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ModalManager modals = ModalManager.getInstance();

        // Force mouse pointer out of the screen while the pointer belongs to the modal layer. This
        // prevents activating hover effects for objects behind it.
        boolean pointerOnModalLayer = modals.claimsPointer(mouseX, mouseY);

        super.extractRenderState(graphics, pointerOnModalLayer ? -1 : mouseX, pointerOnModalLayer ? -1 : mouseY, a);
        modals.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void added() {
        super.added();

        if (script != null) script.onOpened();
    }

    /// Closes the modals this screen hosted.
    @Override
    public void removed() {
        ModalManager.getInstance().closeAllModals();

        if (script != null) script.onClosed();
    }

    // ***************************************************************
    // [ScreenScript] input
    // ***************************************************************
    /// Hands the pointer to every widget, then to the script.
    @Override
    public void mouseMoved(final double mouseX, final double mouseY) {
        for (GuiEventListener child : children()) {
            child.mouseMoved(mouseX, mouseY);
        }

        if (script != null) script.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || script != null && script.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
        return super.mouseReleased(event) || script != null && script.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dragX, final double dragY) {
        return super.mouseDragged(event, dragX, dragY) || script != null && script.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || script != null && script.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        return super.keyPressed(event) || script != null && script.keyPressed(event);
    }

    @Override
    public boolean keyReleased(final @NonNull KeyEvent event) {
        return super.keyReleased(event) || script != null && script.keyReleased(event);
    }

    @Override
    public boolean charTyped(final @NonNull CharacterEvent event) {
        return super.charTyped(event) || script != null && script.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(final @NonNull PreeditEvent event) {
        return super.preeditUpdated(event) || script != null && script.preeditUpdated(event);
    }

    // ***************************************************************
    // [ModalAccessibleScreen] routing
    // ***************************************************************
    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        AbstractWidget modal = getHoveringModal(x, y);
        if (modal != null) return Optional.of(modal);

        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        if (isModal(focused)) {
            if (focused != null) focused.setFocused(true);
        } else {
            super.setFocused(focused);
        }
    }

    @Override
    public @Nullable GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
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
}
