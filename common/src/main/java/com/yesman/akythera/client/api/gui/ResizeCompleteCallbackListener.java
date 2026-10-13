package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;

/// Widgets that subscribe screen resize completion, not progression.
///
/// Minimize graphic resource accesses in each screen size update by mouse dragging,
/// this will provide a hook for mouse button released and stopped resizing the screen.
///
/// Recommended to be paired with [AnchoredWidget] implementation to automatically
/// resize a widget then call the post-resize tasks (natural flow)
public interface ResizeCompleteCallbackListener {
    void onResizeCompleted();
}
