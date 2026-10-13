package com.yesman.akythera.client.api.gui.modal;

import com.yesman.akythera.client.api.event.BuiltInClientEventHooks;
import com.yesman.akythera.client.api.event.gui.ModalEvent;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/// Owns the modal layer drawn over the screen that hosts it.
///
/// The host screen is [Gui#screen] unless [#setHostScreen] supplies another one. Modals are laid out in
/// the host's rectangle, see [#getViewport].
public final class ModalManager {
    private static final ModalManager INSTANCE = new ModalManager();

    public static ModalManager getInstance() {
        return INSTANCE;
    }

    /// Modal layer drawn after the host screen. First element is the bottom-most
    /// modal; last is the topmost. Inputs route to the topmost only, every layer
    /// renders bottom→top. Use [#openModalWidget] / [#openModalWidgetAtCenter]
    /// to push, [#closeModal] to pop the top, [#closeAllModals] to wipe.
    ///
    /// A topmost [FloatingModal] is the one exception to the input rule: it takes
    /// only what falls on its own frame and leaves the rest to the screen.
    private final Deque<AbstractWidget> modalStack = new ArrayDeque<>();

    /// The modal a right press opened, while that button is still held. It takes no input until the
    /// button comes up, so the drag of that press keeps reaching the screen.
    private @Nullable AbstractWidget rightPressModal;

    /// Supplies the host screen in place of [Gui#screen]. Null, or a null answer, falls back to it.
    private @Nullable Supplier<@Nullable Screen> hostScreen;

    /// Maps a widget's rectangle onto the screen, see [#inWidgetSpace]. Identity outside of it.
    private UnaryOperator<ScreenRectangle> widgetSpaceToScreen = UnaryOperator.identity();

    private ModalManager() {
    }

    /// Supplies the screen hosting the modal layer in place of [Gui#screen], or null to restore it.
    public void setHostScreen(final @Nullable Supplier<@Nullable Screen> hostScreen) {
        this.hostScreen = hostScreen;
    }

    /// The screen hosting the modal layer, or null when no screen is open.
    public @Nullable Screen getHostScreen() {
        Screen screen = hostScreen == null ? null : hostScreen.get();

        return screen != null ? screen : Minecraft.getInstance().gui.screen();
    }

    /// The rectangle modals are laid out in: the host screen's, in its GUI units. Null when no screen is open.
    public @Nullable ScreenRectangle getViewport() {
        Screen screen = getHostScreen();

        return screen == null ? null : screen.getRectangle();
    }

    /// Runs `action` on widgets laid out in a space other than the screen's, such as the nodes of a zoomed
    /// canvas. `toScreen` maps a rectangle of that space onto the screen, so a modal those widgets open
    /// lands where they are drawn, see [#screenRectangle].
    public <T> T inWidgetSpace(final UnaryOperator<ScreenRectangle> toScreen, final Supplier<T> action) {
        UnaryOperator<ScreenRectangle> outer = widgetSpaceToScreen;
        widgetSpaceToScreen = toScreen;

        try {
            return action.get();
        } finally {
            widgetSpaceToScreen = outer;
        }
    }

    /// Where `widget` is drawn on the screen: its own rectangle, or that rectangle mapped by the
    /// [#inWidgetSpace] call running now.
    public ScreenRectangle screenRectangle(final AbstractWidget widget) {
        return widgetSpaceToScreen.apply(widget.getRectangle());
    }

    /// Push a modal at the desired location on top of the stack. While the stack
    /// is non-empty, all inputs intercept to the topmost modal. Lower layers stay
    /// visible behind it.
    ///
    /// Callers that need the existing topmost modal replaced must call [#closeModal] first.
    public void openModalWidget(final @NonNull AbstractWidget modal, final int x, final int y) {
        ScreenRectangle viewport = getViewport();

        if (viewport == null) {
            throw new IllegalStateException("Tried to open a modal with no screen to host it");
        }

        modal.setPosition(x, y);
        modal.active = true;
        modal.visible = true;

        if (modal instanceof AnchoredWidget anchoredWidget) {
            anchoredWidget.relocate(viewport);
        }

        rightPressModal = isRightButtonDown() ? modal : null;
        modalStack.addLast(modal);

        BuiltInClientEventHooks.Gui.MODAL_OPENED.post(new ModalEvent.Opened(modal));
    }

    /// Push `modal` as a dropdown of `anchor`. Default placement is the anchor's bottom-left, as wide as
    /// the anchor but never narrower than the modal's own width (so a narrow cell still gets a readable
    /// list); the height is the modal's own.
    ///
    /// The placement then adapts, once per axis, to keep the modal inside [#getViewport]:
    ///
    ///   - clipped on the **right** — the right edges are flushed instead of the left ones
    ///     (`anchor.right == modal.right`);
    ///   - clipped at the **bottom** — the modal flips above the anchor
    ///     (`anchor.top == modal.bottom`).
    ///
    /// Nothing beyond that is guarded: a modal wider / taller than the viewport itself still runs off
    /// the opposite edge. The anchor's edges are read off [#screenRectangle].
    ///
    /// A resized dropdown reopens at the size it was dragged to: a [DropdownSizeMemory] anchor's
    /// remembered size replaces the derived one, outranking the anchor-width rule, and a
    /// [ResizableFrame] modal has its size sink pointed back at that memory.
    public void openModalWidgetUnder(final @NonNull AbstractWidget anchor, final @NonNull AbstractWidget modal) {
        openModalWidgetUnder(anchor, modal, true);
    }

    /// [#openModalWidgetUnder(AbstractWidget, AbstractWidget)], keeping the modal's own width instead of widening
    /// it to the anchor's while `matchAnchorWidth` is false.
    public void openModalWidgetUnder(final @NonNull AbstractWidget anchor, final @NonNull AbstractWidget modal, final boolean matchAnchorWidth) {
        ScreenRectangle viewport = getViewport();

        if (viewport == null) {
            throw new IllegalStateException("Tried to open a modal with no screen to host it");
        }

        ScreenRectangle anchorRect = screenRectangle(anchor);

        int width = matchAnchorWidth ? Math.max(anchorRect.width(), modal.getWidth()) : modal.getWidth();
        int height = modal.getHeight();

        if (anchor instanceof DropdownSizeMemory memory) {
            if (memory.dropdownWidth() > 0) width = memory.dropdownWidth();
            if (memory.dropdownHeight() > 0) height = memory.dropdownHeight();

            if (modal instanceof ResizableFrame resizable) {
                resizable.frameResizer().setSizeSink(memory::rememberDropdownSize);
            }
        }

        int x = anchorRect.left() + width > viewport.right() ? anchorRect.right() - width : anchorRect.left();
        int y = anchorRect.bottom() + height > viewport.bottom() ? anchorRect.top() - height : anchorRect.bottom();

        // [#openModalWidget] re-derives an AnchoredWidget's rect from its units, so the placement has
        // to travel in those rather than in setPosition/setWidth.
        if (modal instanceof AnchoredWidget anchored) {
            anchored.setX1(AnchoredWidget.pixel(x));
            anchored.setY1(AnchoredWidget.pixel(y));

            // x2 / y2 are the size only under these anchor types — the others read them as
            // opposite-edge insets, where a size would be nonsense, so such a modal keeps sizing itself.
            if (anchored.getHorizontalAnchorType() == AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH) {
                anchored.setX2(AnchoredWidget.pixel(width));
            }

            if (anchored.getVerticalAnchorType() == AnchoredWidget.VerticalAnchorType.TOP_HEIGHT) {
                anchored.setY2(AnchoredWidget.pixel(height));
            }
        }

        openModalWidget(modal, x, y);
    }

    /// Push a modal at the center of [#getViewport].
    public void openModalWidgetAtCenter(final @NonNull AbstractWidget modal) {
        ScreenRectangle viewport = getViewport();

        if (viewport == null) {
            throw new IllegalStateException("Tried to open a modal with no screen to host it");
        }

        int x = viewport.getCenterInAxis(ScreenAxis.HORIZONTAL) - modal.getWidth() / 2;
        int y = viewport.getCenterInAxis(ScreenAxis.VERTICAL) - modal.getHeight() / 2;

        openModalWidget(modal, x, y);
    }

    /// Pop the topmost modal widget.
    public void closeModal() {
        if (modalStack.isEmpty()) return;

        AbstractWidget top = modalStack.removeLast();

        top.active = false;
        top.visible = false;
        top.setFocused(false);

        if (top instanceof CloseableWidget cw) cw.destroy();

        // A FloatingModal is focused through the screen's own focus field rather than standing in for
        // it, so the screen is left pointing at a widget that is no longer up unless it is told.
        Screen screen = getHostScreen();
        if (screen != null && screen.getFocused() == top) screen.setFocused(null);

        BuiltInClientEventHooks.Gui.MODAL_CLOSED.post(new ModalEvent.Closed(top));
    }

    /// Pop every modal off the stack.
    public void closeAllModals() {
        while (!modalStack.isEmpty()) closeModal();
    }

    /// Returns the topmost modal, or `null` when the stack is empty.
    public @Nullable AbstractWidget getModal() {
        return modalStack.peekLast();
    }

    /// The topmost modal that takes input: [#getModal], except while [#rightPressModal] is held.
    public @Nullable AbstractWidget getInputModal() {
        AbstractWidget modal = modalStack.peekLast();

        if (modal == null || modal != rightPressModal) return modal;
        if (isRightButtonDown()) return null;

        rightPressModal = null;
        return modal;
    }

    private boolean isRightButtonDown() {
        Minecraft minecraft = Minecraft.getInstance();

        return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    /// All open modals in render order (bottom-most first, topmost last).
    public Iterable<AbstractWidget> getModals() {
        return modalStack;
    }

    /// Whether any modal is currently open.
    public boolean hasModal() {
        return !modalStack.isEmpty();
    }

    /// Lays every modal out against [#getViewport] again, after the host screen was resized.
    public void relocateModals() {
        ScreenRectangle viewport = getViewport();

        if (viewport == null) return;

        for (AbstractWidget modal : modalStack) {
            if (modal instanceof AnchoredWidget anchoredModal) {
                anchoredModal.relocate(viewport);
            }

            if (modal instanceof ResizeCompleteCallbackListener resizeCallbackListener) {
                resizeCallbackListener.onResizeCompleted();
            }
        }
    }

    /// Whether the pointer belongs to the modal layer rather than the host screen. A [FloatingModal]
    /// claims it only over its own frame.
    public boolean claimsPointer(final int mouseX, final int mouseY) {
        AbstractWidget top = modalStack.peekLast();

        return top != null && (!(top instanceof FloatingModal) || top.isMouseOver(mouseX, mouseY));
    }

    /// Renders every layer bottom→top, after the host screen. Only the topmost sees real mouse coords
    /// so hover/tooltip belongs to it; lower layers run with (-1, -1).
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        AbstractWidget top = modalStack.peekLast();

        for (AbstractWidget m : modalStack) {
            if (!m.isActive()) continue;
            boolean isTop = m == top;
            m.extractRenderState(graphics, isTop ? mouseX : -1, isTop ? mouseY : -1, a);
        }

        if (top != null) {
            graphics.extractDeferredElements(mouseX, mouseY, a);
        }
    }
}
