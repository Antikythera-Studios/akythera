package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

/// A placeable object in [Model3DViewer]. Allows duplicated type
/// under a clear objective, (Two entity models can be placed, but
/// two background components make no sense) accessible by index.
public interface Model3DViewerComponent {
    /// [#drawOrder] band for components drawn after the scene. They still depth-test against it, so
    /// this only settles the order they are emitted in.
    int OVERLAY_DRAW_ORDER = 1000;

    void extractComponent(
        GuiGraphicsExtractor graphics,
        Model3DViewer model3DViewer,
        PoseStack poseStack,
        ByteBufferBuilder byteBuffer,
        int mouseX,
        int mouseY,
        float a
    );

    int drawOrder();

    /// Draws flat over the finished scene, in screen coordinates — text and markers that must not sit
    /// under the scene image. Runs after every [#extractComponent], in [#drawOrder].
    default void extractOverlay(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, int mouseX, int mouseY, float a) {
    }

    default void onResized() {
    }

    default void destroy() {
    }

    /// Whether a left-click may focus this component.
    default boolean canBeFocused(final Model3DViewer owner) {
        return false;
    }

    /// Distance along `ray` at which it hits this component, negative for a miss. The nearest hit
    /// among the [#canBeFocused] components wins the click.
    default float rayTrace(final Model3DViewer owner, final Ray ray) {
        return -1.0F;
    }

    /// This component just became [Model3DViewer#getFocused].
    default void onFocused(final Model3DViewer owner) {
    }

    /// This component just lost focus to `newFocus`, null when focus was cleared.
    default void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
    }

    /// Right-press while this component is focused.
    default void showOptions(final Model3DViewer owner) {
    }

    /// Left-press while this component is focused, before the viewer re-picks. Returning true takes
    /// the press: the following drags and the release route here and no new focus is picked.
    default boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        return false;
    }

    /// A drag step of a press taken by [#onMouseDown].
    default void onMouseDragged(final Model3DViewer owner, final MouseButtonEvent event, final double dx, final double dy) {
    }

    /// End of a press taken by [#onMouseDown].
    default void onMouseReleased(final Model3DViewer owner, final MouseButtonEvent event) {
    }

    /// A press taken by [#onMouseDown] was aborted — restore whatever it was editing to the value it
    /// started from. [#onMouseReleased] does not follow.
    default void onDragCancelled(final Model3DViewer owner) {
    }

    /// A key press while this component is [Model3DViewer#getFocused]. Returning true consumes it.
    default boolean onKeyPressed(final Model3DViewer owner, final KeyEvent event) {
        return false;
    }

    /// Whether a gesture no mouse button is held for is running. While one is, the viewer routes the
    /// next left press to [#onCaptureConfirmed] and the next right press to [#onDragCancelled],
    /// instead of picking or opening the options menu.
    default boolean isCapturingMouse(final Model3DViewer owner) {
        return false;
    }

    /// A left press ending a gesture reported by [#isCapturingMouse] — commit what it was editing.
    default void onCaptureConfirmed(final Model3DViewer owner) {
    }
}