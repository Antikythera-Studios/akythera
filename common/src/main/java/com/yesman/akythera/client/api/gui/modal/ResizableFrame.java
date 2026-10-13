package com.yesman.akythera.client.api.gui.modal;

/// A modal whose frame the user can drag to resize, by way of a held [FrameResizer]. Exposing it lets
/// [ModalManager#openModalWidgetUnder] point the resizer's size sink at the anchor's
/// [DropdownSizeMemory], so a dragged size survives the modal's close.
public interface ResizableFrame {
    FrameResizer frameResizer();
}
