package com.yesman.akythera.client.api.gui.modal;

/// A dropdown anchor that remembers the size its picker was last dragged to. The memory lives on the
/// widget and nowhere else, so it holds for as long as the screen keeps that widget around and is gone
/// once the screen is rebuilt.
///
/// Read and wired by [ModalManager#openModalWidgetUnder], which opens a [ResizableFrame] at the
/// remembered size instead of the default one.
public interface DropdownSizeMemory {
    /// Remembered width, or 0 while the dropdown has never been resized.
    int dropdownWidth();

    /// Remembered height, or 0 while the dropdown has never been resized.
    int dropdownHeight();

    void rememberDropdownSize(int width, int height);
}
